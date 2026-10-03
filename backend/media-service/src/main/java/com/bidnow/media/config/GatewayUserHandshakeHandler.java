package com.bidnow.media.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Names the STOMP session after the gateway-verified user so {@code convertAndSendToUser(userId, …)}
 * reaches them. The gateway strips client-supplied X-User-Id and only injects it from a valid JWT.
 * No header → anonymous session (may still subscribe to public /topic destinations).
 */
public class GatewayUserHandshakeHandler extends DefaultHandshakeHandler {

    static final String X_USER_ID = "X-User-Id";

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String userId = request.getHeaders().getFirst(X_USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return () -> userId;
    }
}
