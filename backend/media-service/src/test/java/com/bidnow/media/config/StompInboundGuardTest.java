package com.bidnow.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompInboundGuardTest {

    private final StompInboundGuard guard = new StompInboundGuard();

    private static Message<byte[]> msg(StompCommand cmd, String destination, boolean withUser) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(cmd);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (withUser) {
            accessor.setUser(() -> "u1");
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void send_toTopic_isRejected() {
        assertThatThrownBy(() -> guard.preSend(msg(StompCommand.SEND, "/topic/auctions/" + UUID.randomUUID(), false), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void send_toUserQueue_isRejected() {
        assertThatThrownBy(() -> guard.preSend(
                msg(StompCommand.SEND, "/user/" + UUID.randomUUID() + "/queue/notifications", true), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_auctionTopic_anonymousAllowed() {
        Message<byte[]> m = msg(StompCommand.SUBSCRIBE, "/topic/auctions/" + UUID.randomUUID(), false);
        assertThat(guard.preSend(m, null)).isSameAs(m);
    }

    @Test
    void subscribe_otherTopic_isRejected() {
        assertThatThrownBy(() -> guard.preSend(msg(StompCommand.SUBSCRIBE, "/topic/other", true), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_nonUuidAuction_isRejected() {
        assertThatThrownBy(() -> guard.preSend(msg(StompCommand.SUBSCRIBE, "/topic/auctions/not-a-uuid", true), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_userNotifications_requiresUser() {
        Message<byte[]> ok = msg(StompCommand.SUBSCRIBE, "/user/queue/notifications", true);
        assertThat(guard.preSend(ok, null)).isSameAs(ok);
        assertThatThrownBy(() -> guard.preSend(msg(StompCommand.SUBSCRIBE, "/user/queue/notifications", false), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_directResolvedQueue_isRejected() {
        assertThatThrownBy(() -> guard.preSend(
                msg(StompCommand.SUBSCRIBE, "/queue/notifications-user123", true), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_passes() {
        Message<byte[]> m = msg(StompCommand.CONNECT, null, false);
        assertThat(guard.preSend(m, null)).isSameAs(m);
    }

    @Test
    void messageFrameToTopic_isRejected() {
        assertThatThrownBy(() -> guard.preSend(
                msg(StompCommand.MESSAGE, "/topic/auctions/" + UUID.randomUUID(), false), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void messageFrameToUserQueue_isRejected() {
        assertThatThrownBy(() -> guard.preSend(
                msg(StompCommand.MESSAGE, "/user/" + UUID.randomUUID() + "/queue/notifications", true), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void unsubscribeAndDisconnect_pass() {
        Message<byte[]> u = msg(StompCommand.UNSUBSCRIBE, null, false);
        Message<byte[]> d = msg(StompCommand.DISCONNECT, null, false);
        assertThat(guard.preSend(u, null)).isSameAs(u);
        assertThat(guard.preSend(d, null)).isSameAs(d);
    }
}
