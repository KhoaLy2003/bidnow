package com.bidnow.media.notification.batch;

import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.bidnow.media.notification.NotificationFormats.money;

/** In-app copy for outbid / new-bid alerts. In-app only: no email (MVP: real-time channel). */
public final class BidAlerts {

    private static final String OUTBID_TITLE = "You've been outbid";
    private static final String NEW_BID_TITLE = "New bid on your auction";

    private BidAlerts() {
    }

    public static NotificationIntent immediate(BidAlert alert) {
        String title = quoted(alert.auctionTitle(), alert.kind());
        String message = alert.kind() == BidAlertKind.OUTBID
                ? "Someone outbid you on " + title + "." + price(alert.amount())
                : title + " received a new bid." + price(alert.amount());
        return new NotificationIntent(alert.userId(), alert.kind().type(),
                DedupKeys.bidAlert(alert.kind().type(), alert.bidId()), alert.auctionId(), title(alert.kind()),
                message, NotificationLinks.auctionPath(alert.auctionId()), metadata(alert.amount(), null), null);
    }

    public static NotificationIntent batched(DueBatch batch) {
        String title = quoted(batch.auctionTitle(), batch.kind());
        int n = batch.count();
        String message = batch.kind() == BidAlertKind.OUTBID
                ? "You've been outbid " + n + (n == 1 ? " more time" : " more times") + " on " + title + "."
                        + price(batch.latestAmount())
                : title + " received " + n + (n == 1 ? " more bid" : " more bids") + "." + price(batch.latestAmount());
        return new NotificationIntent(batch.userId(), batch.kind().type(),
                DedupKeys.batch(batch.kind().type(), batch.auctionId(), batch.windowStart()), batch.auctionId(),
                title(batch.kind()), message, NotificationLinks.auctionPath(batch.auctionId()),
                metadata(batch.latestAmount(), n), null);
    }

    private static String title(BidAlertKind kind) {
        return kind == BidAlertKind.OUTBID ? OUTBID_TITLE : NEW_BID_TITLE;
    }

    private static String quoted(String auctionTitle, BidAlertKind kind) {
        return auctionTitle == null || auctionTitle.isBlank() ? (kind == BidAlertKind.OUTBID ? "this auction" : "your auction") : "\"" + auctionTitle + "\"";
    }

    private static String price(BigDecimal amount) {
        return amount == null ? "" : " Current price: " + money(amount) + ".";
    }

    private static Map<String, Object> metadata(BigDecimal amount, Integer count) {
        Map<String, Object> metadata = new HashMap<>();
        if (amount != null) {
            metadata.put("currentPrice", amount.toPlainString());
        }
        if (count != null) {
            metadata.put("count", count);
        }
        return metadata;
    }
}
