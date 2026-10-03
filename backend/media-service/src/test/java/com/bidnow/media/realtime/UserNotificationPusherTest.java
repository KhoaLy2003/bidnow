package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserNotificationPusherTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private UserNotificationPusher pusher;

    private final UserNotificationMessage message = UserNotificationMessage.notification(
            NotificationResponse.builder().id(UUID.randomUUID()).type("AUCTION_WON").build(), 2);

    @Test
    void push_sendsToTheUsersNotificationQueue() {
        pusher.push(new UserNotificationPush(USER, message));

        verify(messagingTemplate).convertAndSendToUser(USER.toString(), "/queue/notifications", message);
    }

    @Test
    void push_deliveryFailure_isSwallowed() {
        doThrow(new MessageDeliveryException("no session"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> pusher.push(new UserNotificationPush(USER, message))).doesNotThrowAnyException();
    }
}
