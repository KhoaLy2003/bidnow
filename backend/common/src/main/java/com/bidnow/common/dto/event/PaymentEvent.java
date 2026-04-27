package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private UUID auctionId;
    private String auctionTitle;
    private UUID userId;
    private BigDecimal amount;
    private String paymentType; // e.g. REQUIRED, REMINDER_24H, COMPLETED, REFUNDED
}
