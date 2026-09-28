package com.bidnow.wallet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Marker that wallet processed a cancel for this auction; blocks a late payment hold. */
@Entity
@Table(name = "auction_cancellations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionCancellation {

    @Id
    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "cancelled_at", nullable = false)
    private LocalDateTime cancelledAt;
}
