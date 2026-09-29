package com.bidnow.bidding.service;

import com.bidnow.bidding.dto.UserSummariesQuery;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Resolves many users at once: one Redis MGET, then one user-service batch call for the misses.
     * Never throws; users that cannot be resolved are simply absent from the result.
     */
    public Map<UUID, UserSummaryResponse> getAll(Collection<UUID> userIds) {
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(userIds));
        Map<UUID, UserSummaryResponse> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<UUID> misses = new ArrayList<>();
        List<String> cached = multiGet(ids);
        for (int i = 0; i < ids.size(); i++) {
            UserSummaryResponse summary = cached == null ? null : parse(cached.get(i));
            if (summary != null && hasName(summary)) {
                result.put(ids.get(i), summary);
            } else {
                misses.add(ids.get(i));
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        try {
            BaseResponse<List<UserSummaryResponse>> response = userServiceClient.getUserSummaries(new UserSummariesQuery(misses));
            List<UserSummaryResponse> fetched = response == null || response.getData() == null ? List.of() : response.getData();
            for (UserSummaryResponse summary : fetched) {
                if (summary.getId() != null && hasName(summary)) {
                    result.put(summary.getId(), summary);
                    if (cached != null) {
                        // Redis answered the MGET; if it was down, skip pointless (and slow) writes.
                        writeCache(summary.getId(), summary);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("user-service batch summary lookup failed for {} users: {}", misses.size(), ex.getMessage());
        }
        return result;
    }

    private List<String> multiGet(List<UUID> ids) {
        try {
            return redisTemplate.opsForValue().multiGet(ids.stream().map(UserSummaryCacheService::key).toList());
        } catch (RuntimeException ex) {
            log.warn("User summary cache MGET failed: {}", ex.getMessage());
            return null;
        }
    }

    private UserSummaryResponse parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, UserSummaryResponse.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
