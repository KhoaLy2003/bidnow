package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PaymentStatusResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    void createPaymentHold(UUID auctionId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount);

    void cancelPaymentHold(UUID auctionId);

    List<PendingPaymentResponse> getPendingPayments(UUID userId);

    /** Status of the hold for an auction, for its winner or seller; anyone else, or no hold, is a 404. Read-only. */
    PaymentStatusResponse getPaymentStatus(UUID callerUserId, UUID auctionId);

    ConfirmPaymentResponse confirmPayment(UUID callerUserId, UUID auctionId);

    void forfeitExpiredHold(UUID auctionId);

    /** Sends payment reminder #2 for an unpaid hold inside the reminder window; a no-op otherwise. */
    void sendPaymentReminder(UUID auctionId);
}
