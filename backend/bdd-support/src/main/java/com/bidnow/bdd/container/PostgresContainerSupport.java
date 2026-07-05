package com.bidnow.bdd.container;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

public class PostgresContainerSupport {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    public static Map<String, String> properties() {
        return Map.of(
                "spring.datasource.url", POSTGRES.getJdbcUrl(),
                "spring.datasource.username", POSTGRES.getUsername(),
                "spring.datasource.password", POSTGRES.getPassword()
        );
    }
}
