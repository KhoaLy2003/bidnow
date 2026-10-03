package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    void createPaymentHold(UUID auctionId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount);

    void cancelPaymentHold(UUID auctionId);

    List<PendingPaymentResponse> getPendingPayments(UUID userId);

    ConfirmPaymentResponse confirmPayment(UUID callerUserId, UUID auctionId);

    void forfeitExpiredHold(UUID auctionId);
}
