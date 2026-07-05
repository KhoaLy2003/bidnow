package com.bidnow.common;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(
        classes = TracingAutoConfigTest.MinimalConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "management.tracing.sampling.probability=1.0"
        }
)
class TracingAutoConfigTest {

    @Autowired
    private Tracer tracer;

    @Test
    void tracerIsBraveBridge() {
        assertThat(tracer).isInstanceOf(io.micrometer.tracing.brave.bridge.BraveTracer.class);
    }

    @Test
    void spanHasTraceId() {
        Span span = tracer.nextSpan().name("smoke-test").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            assertThat(tracer.currentSpan().context().traceId())
                    .isNotBlank()
                    .matches("[0-9a-f]{16}|[0-9a-f]{32}");
            assertThat(MDC.get("traceId"))
                    .as("Micrometer should populate MDC traceId for logback integration")
                    .isNotBlank();
        } finally {
            assertThatCode(span::end).doesNotThrowAnyException();
        }
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration.class,
            org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration.class
    })
    static class MinimalConfig {
    }
}
