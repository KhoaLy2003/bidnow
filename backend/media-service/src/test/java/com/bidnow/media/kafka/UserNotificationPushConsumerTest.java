package com.bidnow.media.kafka;

import com.bidnow.media.realtime.UserNotificationPush;
import com.bidnow.media.realtime.UserNotificationPusher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserNotificationPushConsumerTest {

    @Mock
    private UserNotificationPusher pusher;

    @InjectMocks
    private UserNotificationPushConsumer consumer;

    @Test
    void onPush_delegatesToThePusher() {
        UserNotificationPush push = new UserNotificationPush(UUID.randomUUID(), null);

        consumer.onPush(push);

        verify(pusher).push(push);
    }

    @Test
    void listener_usesPerInstanceGroupReadingOnlyNewEvents() throws Exception {
        KafkaListener listener = UserNotificationPushConsumer.class
                .getMethod("onPush", UserNotificationPush.class).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly("user-notification-push-topic");
        assertThat(listener.groupId()).isEqualTo("media-push-${random.uuid}");
        assertThat(listener.properties()).containsExactly("auto.offset.reset=latest");
    }
}
