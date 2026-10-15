package com.bidnow.wallet.dto.response;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Status of an auction's payment hold, for its winner or seller. Deliberately no timestamps or deposit detail. */
@Data
@Builder
public class PaymentStatusResponse {
    private UUID auctionId;
    private PaymentHoldStatus status;
    private BigDecimal totalAmount;
}
