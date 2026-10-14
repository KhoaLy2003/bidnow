package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionContextCacheServiceTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID SELLER_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final String KEY = "bidding:auction:b0000000-0000-0000-0000-000000000005:context";

    @Mock
    private AuctionServiceClient auctionServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private AuctionContextCacheService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new AuctionContextCacheService(auctionServiceClient, redisTemplate, objectMapper, 600);
    }

    private static BidContext context() {
        return BidContext.builder()
                .auctionId(AUCTION_ID)
                .title("Vintage Watch")
                .sellerId(SELLER_ID)
                .status("ACTIVE")
                .currentPrice(new BigDecimal("100.00"))
                .bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00"))
                .totalBids(2)
                .endTime(OffsetDateTime.parse("2026-10-01T12:00:00Z"))
                .build();
    }

    private static Request feignRequest() {
        return Request.create(Request.HttpMethod.GET, "/api/v1/internal/auctions/x/bid-context",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    @Test
    void key_hasContractFormat() {
        assertThat(AuctionContextCacheService.key(AUCTION_ID)).isEqualTo(KEY);
    }

    @Test
    void get_cacheHit_returnsCachedContextWithoutFeignCall() throws Exception {
        when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(context()));

        BidContext result = service.get(AUCTION_ID);

        assertThat(result).isEqualTo(context());
        verify(auctionServiceClient, never()).getBidContext(any());
    }

    @Test
    void get_cacheMiss_fetchesFromAuctionServiceAndCachesWithTtl() throws Exception {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        BidContext result = service.get(AUCTION_ID);

        assertThat(result).isEqualTo(context());
        verify(valueOps).set(KEY, objectMapper.writeValueAsString(context()), Duration.ofSeconds(600));
    }

    @Test
    void get_redisReadFails_fallsThroughToFeign() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        assertThat(service.get(AUCTION_ID)).isEqualTo(context());
    }

    @Test
    void get_redisWriteFails_stillReturnsContext() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));
        doThrow(new RedisConnectionFailureException("down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThat(service.get(AUCTION_ID)).isEqualTo(context());
    }

    @Test
    void get_corruptCachedJson_isEvictedAndRefetched() {
        when(valueOps.get(KEY)).thenReturn("{not json");
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        assertThat(service.get(AUCTION_ID)).isEqualTo(context());
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void get_auctionNotFound_throwsNotFound() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new FeignException.NotFound("not found", feignRequest(), null, null));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_FOUND);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void get_auctionServiceError_throwsServiceUnavailable() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new FeignException.InternalServerError("boom", feignRequest(), null, null));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void get_auctionServiceTimeout_throwsServiceUnavailable() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, feignRequest()));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void get_emptyResponseBody_throwsServiceUnavailable() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(null));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void refresh_evictsFetchesCachesWithTtlAndReturnsFresh() throws Exception {
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        BidContext result = service.refresh(AUCTION_ID);

        assertThat(result).isEqualTo(context());
        org.mockito.InOrder order = inOrder(redisTemplate, auctionServiceClient, valueOps);
        order.verify(redisTemplate).delete(KEY);
        order.verify(auctionServiceClient).getBidContext(AUCTION_ID);
        order.verify(valueOps).set(KEY, objectMapper.writeValueAsString(context()), Duration.ofSeconds(600));
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void refresh_auctionNotFound_throwsNotFound() {
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new FeignException.NotFound("not found", feignRequest(), null, null));

        assertThatThrownBy(() -> service.refresh(AUCTION_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_FOUND);
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void put_writesJsonWithTtl() throws Exception {
        service.put(context());

        verify(valueOps).set(eq(KEY), eq(objectMapper.writeValueAsString(context())), eq(Duration.ofSeconds(600)));
    }

    @Test
    void evict_deletesKey() {
        service.evict(AUCTION_ID);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void evict_redisFailure_isSwallowed() {
        when(redisTemplate.delete(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        service.evict(AUCTION_ID);
    }
}
