package com.bidnow.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepositLockStatusResponse {
    private boolean locked;
    private BigDecimal amount;
    private String status;
    private LocalDateTime lockedAt;
}
