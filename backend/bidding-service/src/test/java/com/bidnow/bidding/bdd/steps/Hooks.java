// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/Hooks.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Resets external state before every scenario: WireMock stubs/requests and all Redis keys. */
@RequiredArgsConstructor
public class Hooks {

    private final StringRedisTemplate redisTemplate;

    @Before
    public void resetExternalState() {
        WireMockSupport.reset();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
