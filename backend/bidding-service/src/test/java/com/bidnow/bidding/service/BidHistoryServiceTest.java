package com.bidnow.bidding.service;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidHistoryServiceTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Mock
    private BidRepository bidRepository;
    @Mock
    private UserSummaryCacheService userSummaries;

    @InjectMocks
    private BidHistoryService service;

    private static Bid bid(UUID bidder, String amount, LocalDateTime at, boolean antiSnipe) {
        Bid bid = Bid.builder().id(UUID.randomUUID()).auctionId(AUCTION_ID).bidderId(bidder)
                .amount(new BigDecimal(amount)).antiSnipingTriggered(antiSnipe).build();
        bid.setCreatedAt(at);
        return bid;
    }

    private static BidHistoryQuery query(int page, int size) {
        BidHistoryQuery q = new BidHistoryQuery();
        q.setPage(page);
        q.setSize(size);
        return q;
    }

    @Test
    void auctionHistory_mapsBidsWithNamesFromOneBatchLookup() {
        List<Bid> bids = List.of(bid(BOB, "110.00", T0.plusMinutes(2), true),
                bid(ALICE, "105.00", T0.plusMinutes(1), false),
                bid(BOB, "100.00", T0, false));
        when(bidRepository.findByAuctionId(eq(AUCTION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(bids, PageRequest.of(0, 20), 3));
        when(userSummaries.getAll(any())).thenReturn(Map.of(
                ALICE, UserSummaryResponse.builder().id(ALICE).name("Alice").avatarUrl("https://cdn/a.png").build()));

        PageResponse<BidHistoryResponse> page = service.auctionHistory(AUCTION_ID, query(0, 20));

        assertThat(page.getData()).hasSize(3);
        BidHistoryResponse first = page.getData().get(0);
        assertThat(first.getBidderId()).isEqualTo(BOB);
        assertThat(first.getBidderName()).isEqualTo("Unknown bidder");
        assertThat(first.getBidderAvatarUrl()).isNull();
        assertThat(first.getAmount()).isEqualByComparingTo("110.00");
        assertThat(first.isAntiSnipingTriggered()).isTrue();
        assertThat(first.getPlacedAt()).isEqualTo(T0.plusMinutes(2).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        assertThat(page.getData().get(1).getBidderName()).isEqualTo("Alice");
        assertThat(page.getData().get(1).getBidderAvatarUrl()).isEqualTo("https://cdn/a.png");
        assertThat(page.getPagination().getTotal()).isEqualTo(3);
        assertThat(page.getPagination().getLimit()).isEqualTo(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userSummaries, times(1)).getAll(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void auctionHistory_requestsNewestFirstWithAmountTieBreak() {
        when(bidRepository.findByAuctionId(eq(AUCTION_ID), any(Pageable.class))).thenReturn(Page.empty());

        service.auctionHistory(AUCTION_ID, query(2, 50));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bidRepository).findByAuctionId(eq(AUCTION_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("amount")));
    }

    @Test
    void emptyAuction_returnsEmptyPageWithoutNameLookup() {
        when(bidRepository.findByAuctionId(eq(AUCTION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<BidHistoryResponse> page = service.auctionHistory(AUCTION_ID, query(0, 20));

        assertThat(page.getData()).isEmpty();
        assertThat(page.getPagination().getTotal()).isZero();
        verify(userSummaries, never()).getAll(any());
    }

    @Test
    void myBids_queriesByAuctionAndBidder() {
        when(bidRepository.findByAuctionIdAndBidderId(eq(AUCTION_ID), eq(ALICE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bid(ALICE, "105.00", T0, false)), PageRequest.of(0, 20), 1));
        when(userSummaries.getAll(any())).thenReturn(Map.of(
                ALICE, UserSummaryResponse.builder().id(ALICE).name("Alice").build()));

        PageResponse<BidHistoryResponse> page = service.myBids(AUCTION_ID, ALICE, query(0, 20));

        assertThat(page.getData()).extracting(BidHistoryResponse::getBidderId).containsExactly(ALICE);
        verify(bidRepository, never()).findByAuctionId(any(), any());
    }
}
