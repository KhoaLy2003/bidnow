package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationIntent.EmailSpec;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.deadline;
import static com.bidnow.media.notification.NotificationFormats.money;

/**
 * Wallet events → winner / seller / bidder notifications. PaymentEvent REQUIRED carries the single
 * "you won, please pay" email (roadmap Decision 3); payment emails are transactional (Decision 5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;
    private final NotificationLinks links;

    public void paymentEvent(PaymentEvent event) {
        if (event.getUserId() == null || event.getAuctionId() == null) {
            log.warn("PaymentEvent {} without winner or auction - notification skipped", event.getPaymentType());
            return;
        }
        switch (String.valueOf(event.getPaymentType())) {
            case "REQUIRED" -> paymentRequired(event);
            case "COMPLETED" -> paymentCompleted(event);
            case "FAILED" -> paymentFailed(event);
            default -> log.debug("PaymentEvent {} for auction {} has no notification",
                    event.getPaymentType(), event.getAuctionId());
        }
    }

    public void depositRefunded(DepositRefundedEvent event) {
        if (event.getUserId() == null || event.getAuctionId() == null) {
            log.warn("DepositRefundedEvent without user or auction - notification skipped");
            return;
        }
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, null);
        // The seeded DEPOSIT_REFUNDED copy says "because you did not win"; cancellations are covered by AUCTION_CANCELLED
        EmailSpec email = "AUCTION_LOST".equals(event.getReason())
                ? new EmailSpec("DEPOSIT_REFUNDED",
                        Map.of("auctionTitle", title, "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), false)
                : null;
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.DEPOSIT_REFUNDED,
                DedupKeys.refund(auctionId), auctionId, "Deposit refunded",
                "Your deposit of " + money(event.getAmount()) + " for \"" + title + "\" was refunded to your wallet.",
                NotificationLinks.WALLET_PATH, null, email));
    }

    private void paymentRequired(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String due = deadline(event.getDeadline());
        String byDeadline = event.getDeadline() == null ? "" : " by " + due;
        String message;
        if (isZero(event.getRemaining())) {
            message = "You won \"" + title + "\". Please confirm your payment of " + money(event.getAmount())
                    + byDeadline + ".";
        } else if (Boolean.TRUE.equals(event.getInsufficientFunds())) {
            message = "You won \"" + title + "\". Please top up your wallet and pay " + money(event.getRemaining())
                    + byDeadline + ".";
        } else {
            message = "You won \"" + title + "\". Please complete your payment of " + money(event.getRemaining())
                    + byDeadline + ".";
        }
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_REQUIRED,
                DedupKeys.payment("REQUIRED", auctionId), auctionId, "Payment required", message,
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("AUCTION_WON", Map.of(
                        "auctionTitle", title,
                        "bidAmount", money(event.getAmount()),
                        "paymentDeadline", due,
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
    }

    private void paymentCompleted(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String dedupKey = DedupKeys.payment("COMPLETED", auctionId);
        List<NotificationIntent> intents = new ArrayList<>();
        intents.add(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_RECEIVED, dedupKey, auctionId,
                "Payment complete", "You paid " + money(event.getAmount()) + " for \"" + title + "\".",
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("PAYMENT_SUCCESSFUL", Map.of(
                        "auctionTitle", title,
                        "bidAmount", money(event.getAmount()),
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
        UUID seller = event.getSellerId() != null ? event.getSellerId() : auctions.sellerId(auctionId).orElse(null);
        if (seller != null) {
            intents.add(new NotificationIntent(seller, NotificationType.PAYMENT_RECEIVED, dedupKey, auctionId,
                    "Buyer paid", "The buyer paid " + money(event.getAmount()) + " for \"" + title
                            + "\". The amount was credited to your wallet.",
                    NotificationLinks.SELLER_AUCTIONS_PATH, null,
                    new EmailSpec("SALE_PAYMENT_RECEIVED", Map.of(
                            "auctionTitle", title,
                            "bidAmount", money(event.getAmount()),
                            "actionUrl", links.absolute(NotificationLinks.SELLER_AUCTIONS_PATH)), true)));
        }
        dispatcher.dispatchAll(intents);
    }

    private void paymentFailed(PaymentEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        dispatcher.dispatch(new NotificationIntent(event.getUserId(), NotificationType.PAYMENT_FAILED,
                DedupKeys.payment("FAILED", auctionId), auctionId, "Payment deadline missed",
                isZero(event.getDepositAmount())
                        ? "The payment deadline for \"" + title + "\" has passed, so your win was cancelled."
                        : "The payment deadline for \"" + title + "\" has passed. Your deposit of "
                                + money(event.getDepositAmount()) + " was forfeited.",
                NotificationLinks.WALLET_PATH, null,
                new EmailSpec("PAYMENT_FAILED", Map.of(
                        "auctionTitle", title,
                        "depositAmount", money(event.getDepositAmount()),
                        "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), true)));
    }

    private static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }
}
