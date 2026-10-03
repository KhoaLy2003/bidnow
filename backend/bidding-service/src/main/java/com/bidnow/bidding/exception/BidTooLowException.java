package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.exception.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

@Getter
public class BidTooLowException extends BaseException {

    private final BigDecimal minimumBid;

    public BidTooLowException(BigDecimal minimumBid) {
        super("Bid must be at least " + minimumBid.toPlainString(), BiddingErrorCodes.BID_TOO_LOW, HttpStatus.BAD_REQUEST);
        this.minimumBid = minimumBid;
    }
}
