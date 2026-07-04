/*
 * BidNow Auction System
 */
package com.bidnow.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private ListAppender<ILoggingEvent> appender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
    }

    @Test
    void logsSuccessfulRequestAtInfo() {
        ServerWebExchange exchange = exchange("GET", "/api/v1/auctions/123", "user-42");
        GatewayFilterChain chain = chainSettingStatus(exchange, HttpStatus.OK);

        filter.filter(exchange, chain).block();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("GET")
                .contains("/api/v1/auctions/123")
                .contains("200")
                .contains("userId=user-42");
    }

    @Test
    void logsClientErrorAtWarn() {
        ServerWebExchange exchange = exchange("POST", "/api/v1/auctions", null);
        GatewayFilterChain chain = chainSettingStatus(exchange, HttpStatus.UNAUTHORIZED);

        filter.filter(exchange, chain).block();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("401").contains("userId=-");
    }

    @Test
    void logsServerErrorAtError() {
        ServerWebExchange exchange = exchange("GET", "/api/v1/auctions", null);
        GatewayFilterChain chain = chainSettingStatus(exchange, HttpStatus.INTERNAL_SERVER_ERROR);

        filter.filter(exchange, chain).block();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("500");
    }

    @Test
    void skipsActuatorPaths() {
        ServerWebExchange exchange = exchange("GET", "/actuator/health", null);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(appender.list).isEmpty();
    }

    private ServerWebExchange exchange(String method, String path, String userId) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), path);
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain chainSettingStatus(ServerWebExchange exchange, HttpStatus status) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenAnswer(invocation -> {
            exchange.getResponse().setStatusCode(status);
            return Mono.empty();
        });
        return chain;
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
