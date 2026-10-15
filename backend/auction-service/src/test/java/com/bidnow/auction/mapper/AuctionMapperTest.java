package com.bidnow.auction.mapper;

import com.bidnow.auction.domain.entity.AuctionCategory;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.response.AuctionDetailResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionMapperTest {

    private final AuctionMapper mapper = new AuctionMapperImpl();

    private static AuctionItem.AuctionItemBuilder completedAuction() {
        return AuctionItem.builder()
                .id(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .title("Seiko 6139")
                .description("Chronograph")
                .category(AuctionCategory.builder().id(UUID.randomUUID()).name("Watches").slug("watches").build())
                .startingPrice(new BigDecimal("1000.00"))
                .bidIncrement(new BigDecimal("50.00"))
                .depositAmount(new BigDecimal("100.00"))
                .currentPrice(new BigDecimal("1150.00"))
                .totalBids(27)
                .status(AuctionStatus.COMPLETED)
                .winnerId(UUID.randomUUID());
    }

    @Test
    void toDetailResponse_exposesPaymentDeadlineAndPaidAt() {
        OffsetDateTime deadline = OffsetDateTime.of(2026, 10, 16, 11, 42, 0, 0, ZoneOffset.UTC);
        OffsetDateTime paidAt = OffsetDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZoneOffset.UTC);
        AuctionItem item = completedAuction().paymentDeadline(deadline).winnerPaidAt(paidAt).build();

        AuctionDetailResponse response = mapper.toDetailResponse(item, List.of(), null);

        assertThat(response.getPaymentDeadline()).isEqualTo(deadline);
        assertThat(response.getWinnerPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void toDetailResponse_leavesPaymentFieldsNullBeforeAHoldExists() {
        AuctionItem item = completedAuction().build();

        AuctionDetailResponse response = mapper.toDetailResponse(item, List.of(), null);

        assertThat(response.getPaymentDeadline()).isNull();
        assertThat(response.getWinnerPaidAt()).isNull();
    }
}
