package com.bidnow.wallet.kafka;

import com.bidnow.wallet.domain.enums.RefundReason;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class DepositRefundedApplicationEvent extends ApplicationEvent {

    private final UUID userId;
    private final UUID walletId;
    private final UUID auctionId;
    private final BigDecimal amount;
    private final RefundReason reason;
    private final Instant refundedAt;

    public DepositRefundedApplicationEvent(Object source, UUID userId, UUID walletId, UUID auctionId,
                                           BigDecimal amount, RefundReason reason, Instant refundedAt) {
        super(source);
        this.userId = userId;
        this.walletId = walletId;
        this.auctionId = auctionId;
        this.amount = amount;
        this.reason = reason;
        this.refundedAt = refundedAt;
    }
}
