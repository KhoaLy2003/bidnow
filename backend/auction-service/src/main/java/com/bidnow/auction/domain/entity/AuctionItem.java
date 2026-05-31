package com.bidnow.auction.domain.entity;

import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auction_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private AuctionCategory category;

    @Column(name = "starting_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal startingPrice;

    @Column(name = "bid_increment", nullable = false, precision = 15, scale = 2)
    private BigDecimal bidIncrement;

    @Column(name = "buy_now_price", precision = 15, scale = 2)
    private BigDecimal buyNowPrice;

    @Column(name = "deposit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "current_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "current_winner_id")
    private UUID currentWinnerId;

    @Column(name = "total_bids", nullable = false)
    @Builder.Default
    private Integer totalBids = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AuctionStatus status = AuctionStatus.DRAFT;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "original_end_time", nullable = false)
    private OffsetDateTime originalEndTime;

    @Column(name = "extension_count", nullable = false)
    @Builder.Default
    private Integer extensionCount = 0;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "winner_paid_at")
    private OffsetDateTime winnerPaidAt;

    @Column(name = "payment_deadline")
    private OffsetDateTime paymentDeadline;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
