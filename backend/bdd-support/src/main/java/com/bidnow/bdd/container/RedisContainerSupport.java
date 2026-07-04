package com.bidnow.bdd.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class RedisContainerSupport {

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    public static Map<String, String> properties() {
        return Map.of(
                "spring.data.redis.host", REDIS.getHost(),
                "spring.data.redis.port", String.valueOf(REDIS.getMappedPort(6379))
        );
    }
}
