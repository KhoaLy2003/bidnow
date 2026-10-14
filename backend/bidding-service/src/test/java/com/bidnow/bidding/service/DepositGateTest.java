package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.DepositLockCommand;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.WalletServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositGateTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String FLAG = "bidding:deposit:b0000000-0000-0000-0000-000000000005:00000000-0000-0000-0000-00000000000b";

    @Mock
    private WalletServiceClient walletServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private DepositGate gate;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        gate = new DepositGate(walletServiceClient, redisTemplate, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BidContext context(OffsetDateTime endTime) {
        return BidContext.builder()
                .auctionId(AUCTION_ID).sellerId(UUID.randomUUID()).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).totalBids(1).endTime(endTime)
                .build();
    }

    private static BidContext context() {
        return context(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/api/v1/internal/wallet/deposit-lock",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] error(int status, String code) {
        return ("{\"status\":" + status + ",\"errorCode\":\"" + code + "\",\"message\":\"m\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void flagKey_hasContractFormat() {
        assertThat(DepositGate.flagKey(AUCTION_ID, USER_ID)).isEqualTo(FLAG);
    }

    @Test
    void flagPresent_skipsWallet() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(true);

        gate.ensureLocked(USER_ID, context());

        verify(walletServiceClient, never()).lockDeposit(any());
    }

    @Test
    void flagAbsent_locksDepositAndSetsFlagUntil24hAfterEnd() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));

        gate.ensureLocked(USER_ID, context());

        verify(walletServiceClient).lockDeposit(new DepositLockCommand(USER_ID, AUCTION_ID, new BigDecimal("20.00")));
        verify(valueOps).set(FLAG, "1", Duration.ofHours(25));
    }

    @Test
    void flagTtl_hasOneMinuteFloor() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));

        gate.ensureLocked(USER_ID, context(OffsetDateTime.ofInstant(NOW.minusSeconds(25 * 3600), ZoneOffset.UTC)));

        verify(valueOps).set(FLAG, "1", Duration.ofMinutes(1));
    }

    @Test
    void redisReadFailure_stillLocksViaWallet() {
        when(redisTemplate.hasKey(FLAG)).thenThrow(new RedisConnectionFailureException("down"));
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));

        gate.ensureLocked(USER_ID, context());

        verify(walletServiceClient).lockDeposit(any());
    }

    @Test
    void redisWriteFailure_isSwallowed() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));
        doThrow(new RedisConnectionFailureException("down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        gate.ensureLocked(USER_ID, context());
    }

    @Test
    void insufficientBalance_mapsTo403WithDetails_andSetsNoFlag() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(new FeignException.BadRequest("bad", request(), """
                {"status":400,"errorCode":"INSUFFICIENT_BALANCE","message":"m",
                 "errors":{"availableBalance":"10.00","required":"20.00"}}
                """.getBytes(StandardCharsets.UTF_8), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(InsufficientBalanceException.class)
                .satisfies(ex -> assertThat(((InsufficientBalanceException) ex).getDetails())
                        .containsEntry("availableBalance", "10.00").containsEntry("required", "20.00"));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void walletNotActive_mapsTo403() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.Forbidden("f", request(), error(403, "WALLET_NOT_ACTIVE"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.WALLET_NOT_ACTIVE);
    }

    @Test
    void walletNotFound_mapsTo403() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.NotFound("nf", request(), error(404, "WALLET_NOT_FOUND"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.WALLET_NOT_FOUND);
    }

    @Test
    void depositLockClosed_mapsTo409() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.Conflict("c", request(), error(409, "DEPOSIT_LOCK_CLOSED"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.DEPOSIT_LOCK_CLOSED);
    }

    @Test
    void walletServerError_mapsTo503() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.InternalServerError("boom", request(), null, Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void walletTimeout_mapsTo503() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void unexpectedWalletClientError_mapsTo503() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.BadRequest("b", request(), error(400, "INVALID_INPUT"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
