package com.bidnow.auction.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_CATEGORY_COUNTS = "category-auction-counts";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration jsonConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                CACHE_CATEGORIES, jsonConfig.entryTtl(Duration.ofMinutes(5)),
                CACHE_CATEGORY_COUNTS, jsonConfig.entryTtl(Duration.ofSeconds(60))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(jsonConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
