package com.bidnow.bidding.service;

import com.bidnow.bidding.feign.UserServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/** Cached bidder display names for events. Never fails: any problem yields "Unknown bidder". */
@Slf4j
@Service
public class UserSummaryCacheService {

    static final String UNKNOWN_BIDDER = "Unknown bidder";

    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public UserSummaryCacheService(UserServiceClient userServiceClient,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${bidding.cache.user-summary-ttl-seconds:600}") long ttlSeconds) {
        this.userServiceClient = userServiceClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    static String key(UUID userId) {
        return "bidding:user:" + userId + ":summary";
    }

    public String displayName(UUID userId) {
        UserSummaryResponse cached = readCache(userId);
        if (cached != null && hasName(cached)) {
            return cached.getName();
        }
        UserSummaryResponse fresh;
        try {
            BaseResponse<UserSummaryResponse> response = userServiceClient.getUserSummary(userId);
            fresh = response == null ? null : response.getData();
        } catch (RuntimeException ex) {
            log.warn("user-service summary lookup failed for {}: {}", userId, ex.getMessage());
            return UNKNOWN_BIDDER;
        }
        if (fresh == null || !hasName(fresh)) {
            return UNKNOWN_BIDDER;
        }
        writeCache(userId, fresh);
        return fresh.getName();
    }

    private static boolean hasName(UserSummaryResponse summary) {
        return summary.getName() != null && !summary.getName().isBlank();
    }

    private UserSummaryResponse readCache(UUID userId) {
        try {
            String json = redisTemplate.opsForValue().get(key(userId));
            return json == null ? null : objectMapper.readValue(json, UserSummaryResponse.class);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("User summary cache read failed for {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private void writeCache(UUID userId, UserSummaryResponse summary) {
        try {
            redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(summary), ttl);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("User summary cache write failed for {}: {}", userId, ex.getMessage());
        }
    }
}
