package com.bidnow.media.service;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;

/** Account-level notifications. Auction, bid and payment events are handled in notification.handler. */
public interface NotificationService {
    void handleUserVerificationRequested(UserVerificationRequestedEvent event);

    void handleUserRegistered(UserRegisteredEvent event);
}
