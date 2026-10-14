package com.bidnow.media.kafka;

import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.realtime.UserNotificationPush;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationPushPublisherTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserNotificationPushPublisher publisher;

    private final UserNotificationMessage message =
            UserNotificationMessage.notification(NotificationResponse.builder().id(UUID.randomUUID()).build(), 1);

    @Test
    void publish_sendsKeyedByUser() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        publisher.publish(USER, message);

        verify(kafkaTemplate).send("user-notification-push-topic", USER.toString(), new UserNotificationPush(USER, message));
    }

    @Test
    void publish_failedSend_isOnlyLogged() {
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        assertThatCode(() -> publisher.publish(USER, message)).doesNotThrowAnyException();
    }
}
