/*
 * BidNow Auction System
 */
package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AvatarUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaKafkaProducer {

    private static final String AVATAR_UPLOADED_TOPIC = "avatar-uploaded-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAvatarUploaded(AvatarUploadedEvent event) {
        log.info("Publishing AvatarUploadedEvent for userId={}, s3Key={}", event.getUserId(), event.getS3Key());
        kafkaTemplate.send(AVATAR_UPLOADED_TOPIC, event.getUserId().toString(), event);
    }
}
