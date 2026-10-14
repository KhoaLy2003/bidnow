package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.exception.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** The bidder's wallet cannot cover the auction deposit. Details come from wallet-service (availableBalance, required). */
@Getter
public class InsufficientBalanceException extends BaseException {

    private final Map<String, String> details;

    public InsufficientBalanceException(Map<String, String> details) {
        super("Insufficient wallet balance to lock the auction deposit. Please top up your wallet.",
                BiddingErrorCodes.BID_INSUFFICIENT_BALANCE, HttpStatus.FORBIDDEN);
        this.details = Map.copyOf(details);
    }
}
