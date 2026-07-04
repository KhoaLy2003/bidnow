package com.bidnow.wallet.kafka;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class DepositCompletedApplicationEvent extends ApplicationEvent {

    private final UUID userId;
    private final UUID walletId;
    private final BigDecimal amount;
    private final BigDecimal newBalance;

    public DepositCompletedApplicationEvent(Object source, UUID userId, UUID walletId,
                                             BigDecimal amount, BigDecimal newBalance) {
        super(source);
        this.userId = userId;
        this.walletId = walletId;
        this.amount = amount;
        this.newBalance = newBalance;
    }
}
