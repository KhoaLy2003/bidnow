package com.bidnow.gateway.filter;

import com.bidnow.gateway.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AuthenticationFilter filter = new AuthenticationFilter(jwtUtil);

    private static GatewayFilterChain passingChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    @Test
    void auctionBidHistory_isPublic() {
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bids/auction/" + UUID.randomUUID()).build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void myBids_requiresToken() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bids/auction/" + UUID.randomUUID() + "/my-bids").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void placingABid_requiresToken() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/bids").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ServerWebExchange forwarded(GatewayFilterChain chain) {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    @Test
    void websocket_withValidQueryToken_injectsUserAndDropsToken() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        when(jwtUtil.extractRoles("good")).thenReturn("USER");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-notifications/info?t=123&access_token=good").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(out.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("USER");
        assertThat(out.getRequest().getURI().getQuery()).isEqualTo("t=123");
    }

    @Test
    void websocket_withBearerHeader_injectsUser() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws-notifications/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good").build());

        filter.filter(exchange, chain).block();

        assertThat(forwarded(chain).getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
    }

    @Test
    void websocket_anonymous_passesThroughAndStripsSpoofedIdentity() {
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws-notifications/info")
                .header("X-User-Id", "spoofed").header("X-User-Roles", "ADMIN").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
        assertThat(out.getRequest().getHeaders().containsKey("X-User-Roles")).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void websocket_invalidToken_is401() {
        when(jwtUtil.isTokenValid("bad")).thenReturn(false);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-notifications/info?access_token=bad").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void websocket_bearerAndQueryToken_bearerWinsAndQueryTokenDropped() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-notifications/info?access_token=x")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getURI().getQuery()).isNull();
        assertThat(out.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
    }

    @Test
    void websocket_validToken_replacesSpoofedIdentityHeaders() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        when(jwtUtil.extractRoles("good")).thenReturn("USER");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/ws-notifications/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good")
                .header("X-User-Id", "spoofed").header("X-User-Roles", "ADMIN").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getHeaders().get("X-User-Id")).isEqualTo(List.of("user-1"));
        assertThat(out.getRequest().getHeaders().get("X-User-Roles")).isEqualTo(List.of("USER"));
    }

    @Test
    void websocket_invalidBearerWithValidQueryToken_is401() {
        when(jwtUtil.isTokenValid("bad")).thenReturn(false);
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-notifications/info?access_token=good")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void websocket_encodedTokenParamName_isAlsoDropped() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUserId("good")).thenReturn("user-1");
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET,
                        URI.create("/ws-notifications/info?t=1&access%5Ftoken=good")).build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getURI().getRawQuery()).isEqualTo("t=1");
        assertThat(out.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
    }

    @Test
    void stripAccessToken_malformedQuery_throwsIllegalArgument() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> AuthenticationFilter.stripAccessToken(URI.create("/ws-notifications/info?café=1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void websocket_malformedQuery_is400() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("/ws-notifications/info?café=1")).build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void websocket_traversalPath_is400() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("/ws-notifications/../api/v1/media/x")).build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void websocket_encodedSlashOrDot_is400() {
        for (String p : List.of("/ws-notifications/a%2Fb", "/ws-notifications/%2e%2e/x", "/ws-notifications/a%2fb",
                "/ws-notifications/..;/x")) {
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .method(HttpMethod.GET, URI.create(p)).build());

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void internalPath_underServiceNamePrefix_is403() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/auction-service/api/v1/internal/auctions/" + UUID.randomUUID() + "/bid-context")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void publicPath_stripsSpoofedIdentityHeaders() {
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bids/auction/" + UUID.randomUUID())
                        .header("X-User-Id", "spoofed").header("X-User-Roles", "ADMIN").build());

        filter.filter(exchange, chain).block();

        ServerWebExchange out = forwarded(chain);
        assertThat(out.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
        assertThat(out.getRequest().getHeaders().containsKey("X-User-Roles")).isFalse();
    }
}
