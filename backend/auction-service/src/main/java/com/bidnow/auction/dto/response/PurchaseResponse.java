package com.bidnow.auction.dto.response;

import com.bidnow.auction.domain.enums.PurchasePaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An auction the authenticated user won, with its payment status")
public class PurchaseResponse {

    @Schema(description = "Auction UUID")
    private UUID auctionId;

    @Schema(description = "Auction title")
    private String title;

    @Schema(description = "Primary image URL", nullable = true)
    private String primaryImageUrl;

    @Schema(description = "Final price (winning bid or Buy It Now price)")
    private BigDecimal finalPrice;

    @Schema(description = "Payment status derived from the auction row")
    private PurchasePaymentStatus paymentStatus;

    @Schema(description = "When the auction closed with this user as winner")
    private OffsetDateTime completedAt;

    @Schema(description = "Payment deadline (null until the wallet has opened the payment)", nullable = true)
    private OffsetDateTime paymentDeadline;

    @Schema(description = "When payment was recorded (null unless PAID)", nullable = true)
    private OffsetDateTime paidAt;
}
