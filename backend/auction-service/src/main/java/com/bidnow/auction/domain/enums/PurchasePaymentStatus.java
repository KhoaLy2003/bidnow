package com.bidnow.auction.domain.enums;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.exception.BadRequestException;

/**
 * Where a won auction stands in the winner's payment flow, derived from the auction row alone.
 * <p>
 * {@code winnerPaidAt} and the {@code COMPLETED -> FAILED} flip are projected from the wallet's
 * payment events, so this is eventually consistent with the wallet.
 */
public enum PurchasePaymentStatus {

    /** Won, deadline not reached, not yet paid. */
    PENDING_PAYMENT,
    /** Won and paid. */
    PAID,
    /** Won but never paid: the auction was flipped to FAILED after the payment deadline. */
    FORFEITED;

    /**
     * @return the status named by a request parameter, or {@code null} when it is absent or blank
     * @throws BadRequestException when the value is not a known status
     */
    public static PurchasePaymentStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown payment status: " + value, ErrorCodes.INVALID_INPUT);
        }
    }

    /**
     * @return the payment status of a won auction, or {@code null} when the auction is not a
     * purchase (not closed with a winner).
     */
    public static PurchasePaymentStatus of(AuctionItem item) {
        if (item == null || item.getWinnerId() == null) {
            return null;
        }
        if (item.getStatus() == AuctionStatus.COMPLETED) {
            return item.getWinnerPaidAt() == null ? PENDING_PAYMENT : PAID;
        }
        if (item.getStatus() == AuctionStatus.FAILED) {
            return FORFEITED;
        }
        return null;
    }
}
