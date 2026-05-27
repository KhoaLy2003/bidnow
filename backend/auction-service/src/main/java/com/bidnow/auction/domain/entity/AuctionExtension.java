package com.bidnow.auction.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auction_extensions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private AuctionItem auction;

    @Column(name = "previous_end_time", nullable = false)
    private OffsetDateTime previousEndTime;

    @Column(name = "new_end_time", nullable = false)
    private OffsetDateTime newEndTime;

    @Column(name = "extension_duration_seconds", nullable = false)
    private Integer extensionDurationSeconds;

    @Column(name = "triggered_by_bid_id")
    private UUID triggeredByBidId;

    @Column(name = "triggered_by_user_id", nullable = false)
    private UUID triggeredByUserId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
