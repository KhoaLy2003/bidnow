package com.bidnow.auction.service;

import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.request.BuyNowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate 6.4 truncates the remaining transaction time to whole seconds and throws
 * "transaction timeout expired" when that is 0, so {@code timeout = 1} fails every row-locked
 * operation at its first statement. Mocked service tests cannot see this, so guard the annotation.
 */
class AuctionBidServiceTransactionTimeoutTest {

    @Test
    void applyBid_timeoutIsAtLeastTwoSeconds() throws NoSuchMethodException {
        Method method = AuctionBidService.class.getMethod("applyBid", UUID.class, ApplyBidRequest.class);

        assertThat(method.getAnnotation(Transactional.class).timeout()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void applyBuyNow_timeoutIsAtLeastTwoSeconds() throws NoSuchMethodException {
        Method method = AuctionBidService.class.getMethod("applyBuyNow", UUID.class, BuyNowRequest.class);

        assertThat(method.getAnnotation(Transactional.class).timeout()).isGreaterThanOrEqualTo(2);
    }
}
