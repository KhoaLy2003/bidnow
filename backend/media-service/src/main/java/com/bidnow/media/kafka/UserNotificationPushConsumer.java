package com.bidnow.media.kafka;

import com.bidnow.media.realtime.UserNotificationPush;
import com.bidnow.media.realtime.UserNotificationPusher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Every media instance must see every push (each forwards to its own connected sessions), so this listener
 * uses a per-instance consumer group and reads only new events - same pattern as AuctionRealtimeConsumer.
 */
@Component
@RequiredArgsConstructor
public class UserNotificationPushConsumer {

    private static final String PER_INSTANCE_GROUP = "media-push-${random.uuid}";
    private static final String NEW_EVENTS_ONLY = "auto.offset.reset=latest";

    private final UserNotificationPusher pusher;

    @KafkaListener(topics = UserNotificationPushPublisher.TOPIC, groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onPush(UserNotificationPush push) {
        pusher.push(push);
    }
}
