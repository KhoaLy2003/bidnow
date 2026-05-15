/*
 * BidNow Auction System
 */
package com.bidnow.media.strategy.impl;

import com.bidnow.common.dto.event.AvatarUploadedEvent;
import com.bidnow.common.enums.MediaEntityType;
import com.bidnow.media.kafka.MediaKafkaProducer;
import com.bidnow.media.strategy.UploadEventStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Handles post-upload actions for USER_AVATAR uploads.
 * Publishes an {@link AvatarUploadedEvent} so user-service can update avatar_url.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarUploadEventStrategy implements UploadEventStrategy {

    private final MediaKafkaProducer mediaKafkaProducer;

    @Override
    public MediaEntityType getSupportedType() {
        return MediaEntityType.USER_AVATAR;
    }

    @Override
    public void handle(UUID ownerId, UUID entityId, String s3Key) {
        log.info("Handling USER_AVATAR upload for ownerId={}, s3Key={}", ownerId, s3Key);
        mediaKafkaProducer.publishAvatarUploaded(
                AvatarUploadedEvent.builder()
                        .userId(ownerId)
                        .s3Key(s3Key)
                        .build()
        );
    }
}
