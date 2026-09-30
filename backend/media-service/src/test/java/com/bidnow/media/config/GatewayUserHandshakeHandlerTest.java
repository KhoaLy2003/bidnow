package com.bidnow.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayUserHandshakeHandlerTest {

    private final GatewayUserHandshakeHandler handler = new GatewayUserHandshakeHandler();

    private static ServletServerHttpRequest request(String userId) {
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws-notifications/websocket");
        if (userId != null) {
            servlet.addHeader("X-User-Id", userId);
        }
        return new ServletServerHttpRequest(servlet);
    }

    @Test
    void gatewayUserHeader_becomesPrincipal() {
        Principal principal = handler.determineUser(request("550e8400-e29b-41d4-a716-446655440010"), null, new HashMap<>());

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("550e8400-e29b-41d4-a716-446655440010");
    }

    @Test
    void noHeader_isAnonymous() {
        assertThat(handler.determineUser(request(null), null, new HashMap<>())).isNull();
    }

    @Test
    void blankHeader_isAnonymous() {
        assertThat(handler.determineUser(request("  "), null, new HashMap<>())).isNull();
    }
}
