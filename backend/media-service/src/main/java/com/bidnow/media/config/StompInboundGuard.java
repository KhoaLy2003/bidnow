package com.bidnow.media.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Clients are receive-only. Only an allow-list of client commands passes (CONNECT, STOMP, SUBSCRIBE,
 * UNSUBSCRIBE, DISCONNECT, ACK, NACK); SEND and server-only frames such as MESSAGE would otherwise be
 * fanned out by the simple broker, so every other command is rejected. SUBSCRIBE is limited to public
 * auction topics and the caller's own user queue. Heartbeats (no command) pass.
 */
public class StompInboundGuard implements ChannelInterceptor {

    private static final Pattern AUCTION_TOPIC = Pattern.compile("^/topic/auctions/[0-9a-fA-F-]{36}$");
    private static final Set<StompCommand> ALLOWED = Set.of(StompCommand.CONNECT, StompCommand.STOMP,
            StompCommand.SUBSCRIBE, StompCommand.UNSUBSCRIBE, StompCommand.DISCONNECT, StompCommand.ACK,
            StompCommand.NACK);
    private static final String USER_NOTIFICATIONS = "/user/queue/notifications";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (!ALLOWED.contains(command)) {
            throw new MessageDeliveryException("Client command not permitted: " + command);
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            boolean allowed = destination != null && (AUCTION_TOPIC.matcher(destination).matches()
                    || (USER_NOTIFICATIONS.equals(destination) && accessor.getUser() != null));
            if (!allowed) {
                throw new MessageDeliveryException("Subscription denied: " + destination);
            }
        }
        return message;
    }
}
