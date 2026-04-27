package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionCreatedEvent {
    private UUID auctionId;
    private UUID sellerId;
    private String title;
    private BigDecimal startingPrice;
    private LocalDateTime endTime;
}
