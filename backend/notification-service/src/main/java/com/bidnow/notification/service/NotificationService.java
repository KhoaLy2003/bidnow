package com.bidnow.notification.service;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;

public interface NotificationService {
    void handleUserVerificationRequested(UserVerificationRequestedEvent event);

    void handleUserRegistered(UserRegisteredEvent event);

    void handleAuctionCreated(AuctionCreatedEvent event);

    void handleBidPlaced(BidPlacedEvent event);

    void handleAuctionEnded(AuctionEndedEvent event);

    void handlePaymentEvent(PaymentEvent event);
}
