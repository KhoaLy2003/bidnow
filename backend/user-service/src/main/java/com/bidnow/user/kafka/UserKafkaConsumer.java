/*
 * BidNow Auction System
 */
package com.bidnow.user.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AvatarUploadedEvent;
import com.bidnow.user.dto.request.UpdateUserProfileRequest;
import com.bidnow.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Loggable
public class UserKafkaConsumer {

    private final UserProfileService userProfileService;

    @Transactional
    @KafkaListener(topics = "avatar-uploaded-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAvatarUploaded(AvatarUploadedEvent event) {
        log.info("Received AvatarUploadedEvent for userId={}", event.getUserId());

        UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .avatarUrl(event.getPublicUrl())
                .build();
        userProfileService.updateMyProfile(event.getUserId(), request);

        log.info("Updated avatar_url for userId={}", event.getUserId());
    }
}
