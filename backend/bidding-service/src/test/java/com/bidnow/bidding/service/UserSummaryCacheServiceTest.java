package com.bidnow.bidding.service;

import com.bidnow.bidding.dto.UserSummariesQuery;
import com.bidnow.bidding.feign.UserServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSummaryCacheServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String KEY = "bidding:user:00000000-0000-0000-0000-00000000000b:summary";

    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private UserSummaryCacheService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new UserSummaryCacheService(userServiceClient, redisTemplate, objectMapper, 600);
    }

    private static UserSummaryResponse alice() {
        return UserSummaryResponse.builder().id(USER_ID).name("Alice").avatarUrl("https://cdn/a.png").build();
    }

    @Test
    void key_hasContractFormat() {
        assertThat(UserSummaryCacheService.key(USER_ID)).isEqualTo(KEY);
    }

    @Test
    void cacheHit_returnsNameWithoutFeign() throws Exception {
        when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
        verify(userServiceClient, never()).getUserSummary(any());
    }

    @Test
    void cacheMiss_fetchesAndCachesSummary() throws Exception {
        when(valueOps.get(KEY)).thenReturn(null);
        when(userServiceClient.getUserSummary(USER_ID)).thenReturn(BaseResponse.success(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
        verify(valueOps).set(KEY, objectMapper.writeValueAsString(alice()), Duration.ofSeconds(600));
    }

    @Test
    void userServiceFailure_returnsUnknownBidder() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(userServiceClient.getUserSummary(USER_ID)).thenThrow(new RuntimeException("down"));

        assertThat(service.displayName(USER_ID)).isEqualTo(UserSummaryCacheService.UNKNOWN_BIDDER);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blankName_returnsUnknownBidderAndIsNotCached() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(userServiceClient.getUserSummary(USER_ID))
                .thenReturn(BaseResponse.success(UserSummaryResponse.builder().id(USER_ID).name(" ").build()));

        assertThat(service.displayName(USER_ID)).isEqualTo(UserSummaryCacheService.UNKNOWN_BIDDER);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisFailure_fallsThroughToUserService() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(userServiceClient.getUserSummary(USER_ID)).thenReturn(BaseResponse.success(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
    }

    @Test
    void corruptCache_isIgnoredAndRefetched() {
        when(valueOps.get(KEY)).thenReturn("{not json");
        when(userServiceClient.getUserSummary(eq(USER_ID))).thenReturn(BaseResponse.success(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
    }

    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String BOB_KEY = "bidding:user:00000000-0000-0000-0000-00000000000c:summary";

    private static UserSummaryResponse bob() {
        return UserSummaryResponse.builder().id(BOB_ID).name("Bob").build();
    }

    @Test
    void getAll_allCached_makesNoFeignCall() throws Exception {
        when(valueOps.multiGet(List.of(KEY, BOB_KEY))).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(alice()), objectMapper.writeValueAsString(bob())));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result).containsOnlyKeys(USER_ID, BOB_ID);
        assertThat(result.get(USER_ID).getName()).isEqualTo("Alice");
        verify(userServiceClient, never()).getUserSummaries(any());
    }

    @Test
    void getAll_partialHit_fetchesOnlyMissesInOneCallAndCachesThem() throws Exception {
        when(valueOps.multiGet(List.of(KEY, BOB_KEY))).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(alice()), null));
        when(userServiceClient.getUserSummaries(new UserSummariesQuery(List.of(BOB_ID))))
                .thenReturn(BaseResponse.success(List.of(bob())));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result.get(BOB_ID).getName()).isEqualTo("Bob");
        verify(userServiceClient).getUserSummaries(new UserSummariesQuery(List.of(BOB_ID)));
        verify(valueOps).set(BOB_KEY, objectMapper.writeValueAsString(bob()), Duration.ofSeconds(600));
    }

    @Test
    void getAll_userServiceDown_returnsCachedOnly() throws Exception {
        when(valueOps.multiGet(List.of(KEY, BOB_KEY))).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(alice()), null));
        when(userServiceClient.getUserSummaries(any())).thenThrow(new RuntimeException("down"));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result).containsOnlyKeys(USER_ID);
    }

    @Test
    void getAll_redisDown_fetchesAllFromUserService() {
        when(valueOps.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("down"));
        when(userServiceClient.getUserSummaries(any())).thenReturn(BaseResponse.success(List.of(alice(), bob())));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result).containsOnlyKeys(USER_ID, BOB_ID);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getAll_emptyInput_touchesNothing() {
        assertThat(service.getAll(List.of())).isEmpty();
        verify(userServiceClient, never()).getUserSummaries(any());
        verify(valueOps, never()).multiGet(anyList());
    }

    @Test
    void getAll_duplicateIds_areLookedUpOnce() throws Exception {
        when(valueOps.multiGet(List.of(KEY))).thenReturn(Arrays.asList(objectMapper.writeValueAsString(alice())));

        assertThat(service.getAll(List.of(USER_ID, USER_ID))).containsOnlyKeys(USER_ID);
    }
}
