package com.bidnow.wallet.domain.entity;

import com.bidnow.common.entity.BaseEntity;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_holds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auction_id", nullable = false, unique = true)
    private UUID auctionId;

    @Column(name = "winner_wallet_id", nullable = false)
    private UUID winnerWalletId;

    @Column(name = "winner_user_id", nullable = false)
    private UUID winnerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "deposit_applied", nullable = false, precision = 19, scale = 4)
    private BigDecimal depositApplied;

    @Column(name = "deposit_lock_id")
    private UUID depositLockId;

    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingAmount;

    @Column(name = "funds_held", nullable = false)
    private boolean fundsHeld;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentHoldStatus status;

    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
