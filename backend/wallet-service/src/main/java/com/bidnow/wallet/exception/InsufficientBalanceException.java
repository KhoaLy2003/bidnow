package com.bidnow.wallet.exception;

import com.bidnow.common.exception.BadRequestException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsufficientBalanceException extends BadRequestException {

    private final BigDecimal availableBalance;
    private final BigDecimal required;

    public InsufficientBalanceException(BigDecimal availableBalance, BigDecimal required) {
        super("Insufficient available balance: available=" + availableBalance.toPlainString()
                + ", required=" + required.toPlainString(), WalletErrorCodes.INSUFFICIENT_BALANCE);
        this.availableBalance = availableBalance;
        this.required = required;
    }
}
