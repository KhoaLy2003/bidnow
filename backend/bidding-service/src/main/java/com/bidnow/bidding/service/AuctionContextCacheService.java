package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Cache-aside reader of auction-service's bid context. Redis is an optimization only: any Redis
 * failure degrades to a cache miss and never fails the request. auction-service stays the authority.
 */
@Slf4j
@Service
public class AuctionContextCacheService {

    private final AuctionServiceClient auctionServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public AuctionContextCacheService(AuctionServiceClient auctionServiceClient,
                                      StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      @Value("${bidding.cache.context-ttl-seconds:600}") long ttlSeconds) {
        this.auctionServiceClient = auctionServiceClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    static String key(UUID auctionId) {
        return "bidding:auction:" + auctionId + ":context";
    }

    public BidContext get(UUID auctionId) {
        BidContext cached = readCache(auctionId);
        if (cached != null) {
            return cached;
        }
        BidContext fresh = fetch(auctionId);
        put(fresh);
        return fresh;
    }

    /**
     * Drops the cached context and re-reads it from auction-service, caching the fresh value.
     * Same error mapping as {@link #get}.
     */
    public BidContext refresh(UUID auctionId) {
        evict(auctionId);
        BidContext fresh = fetch(auctionId);
        put(fresh);
        return fresh;
    }

    public void put(BidContext ctx) {
        try {
            redisTemplate.opsForValue().set(key(ctx.getAuctionId()), objectMapper.writeValueAsString(ctx), ttl);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Failed to cache bid context for auction {}: {}", ctx.getAuctionId(), ex.getMessage());
        }
    }

    public void evict(UUID auctionId) {
        try {
            redisTemplate.delete(key(auctionId));
        } catch (RuntimeException ex) {
            log.warn("Failed to evict bid context for auction {}: {}", auctionId, ex.getMessage());
        }
    }

    private BidContext readCache(UUID auctionId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key(auctionId));
        } catch (RuntimeException ex) {
            log.warn("Redis read failed for auction {} - treating as cache miss: {}", auctionId, ex.getMessage());
            return null;
        }
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BidContext.class);
        } catch (JsonProcessingException ex) {
            log.warn("Corrupt cached bid context for auction {} - evicting: {}", auctionId, ex.getMessage());
            evict(auctionId);
            return null;
        }
    }

    private BidContext fetch(UUID auctionId) {
        BaseResponse<BidContext> response;
        try {
            response = auctionServiceClient.getBidContext(auctionId);
        } catch (FeignException.NotFound ex) {
            throw new NotFoundException("Auction not found: " + auctionId, BiddingErrorCodes.AUCTION_NOT_FOUND);
        } catch (FeignException ex) {
            log.error("auction-service bid-context call failed for auction {} (status {}): {}",
                    auctionId, ex.status(), ex.getMessage());
            log.debug("auction-service failure detail", ex);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        if (response == null || response.getData() == null) {
            log.error("auction-service returned an empty bid context for auction {}", auctionId);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        return response.getData();
    }
}
