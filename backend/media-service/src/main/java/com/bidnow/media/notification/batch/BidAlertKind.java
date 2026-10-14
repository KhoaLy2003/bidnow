package com.bidnow.media.notification.batch;

import com.bidnow.media.domain.enums.NotificationType;

/** The two batched bid alerts: the previous leader was outbid, or the seller received a bid. */
public enum BidAlertKind {
    OUTBID(NotificationType.BID_OUTBID),
    NEW_BID(NotificationType.NEW_BID);

    private final NotificationType type;

    BidAlertKind(NotificationType type) {
        this.type = type;
    }

    public NotificationType type() {
        return type;
    }
}
