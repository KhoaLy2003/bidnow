package com.bidnow.bidding.service;

import com.bidnow.bidding.dto.AuctionBidSummary;
import com.bidnow.bidding.dto.response.MyActiveBidResponse;
import com.bidnow.bidding.dto.response.MyActiveBidsResponse;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.bidding.repository.projection.BidderAuctionBid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyActiveBidsServiceTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID A1 = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID A3 = UUID.fromString("a0000000-0000-0000-0000-000000000003");

    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionBidGateway auctionBidGateway;
    @InjectMocks
    private MyActiveBidsService service;

    private static BidderAuctionBid bid(UUID auctionId, String highest) {
        return new BidderAuctionBid(auctionId, new BigDecimal(highest), LocalDateTime.of(2026, 10, 16, 9, 0));
    }

    private static AuctionBidSummary summary(UUID auctionId, UUID winner, String price, String endTime) {
        return AuctionBidSummary.builder().auctionId(auctionId).title("Auction " + auctionId.toString().charAt(35))
                .primaryImageUrl("https://cdn/" + auctionId + ".jpg").currentPrice(new BigDecimal(price))
                .currentWinnerId(winner).totalBids(5).endTime(OffsetDateTime.parse(endTime)).build();
    }

    @Test
    void marksAuctionsWonByTheCallerAsWinningAndTheRestAsOutbid() {
        when(bidRepository.findAuctionBidsByBidder(any(), any()))
                .thenReturn(List.of(bid(A1, "120.00"), bid(A2, "200.00")));
        when(auctionBidGateway.activeSummaries(List.of(A1, A2))).thenReturn(List.of(
                summary(A1, ME, "120.00", "2026-10-17T10:00:00Z"),
                summary(A2, OTHER, "230.00", "2026-10-18T10:00:00Z")));

        MyActiveBidsResponse response = service.myActiveBids(ME);

        assertThat(response.getItems()).extracting(MyActiveBidResponse::getAuctionId, MyActiveBidResponse::isWinning)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(A1, true), org.assertj.core.groups.Tuple.tuple(A2, false));
        assertThat(response.getCounts().getAll()).isEqualTo(2);
        assertThat(response.getCounts().getWinning()).isEqualTo(1);
        assertThat(response.getCounts().getOutbid()).isEqualTo(1);
    }

    @Test
    void carriesMyHighestBidAndTheLiveAuctionFields() {
        when(bidRepository.findAuctionBidsByBidder(any(), any())).thenReturn(List.of(bid(A2, "200.00")));
        when(auctionBidGateway.activeSummaries(List.of(A2)))
                .thenReturn(List.of(summary(A2, OTHER, "230.00", "2026-10-18T10:00:00Z")));

        MyActiveBidResponse item = service.myActiveBids(ME).getItems().get(0);

        assertThat(item.getMyHighestBid()).isEqualByComparingTo("200.00");
        assertThat(item.getCurrentPrice()).isEqualByComparingTo("230.00");
        assertThat(item.getTotalBids()).isEqualTo(5);
        assertThat(item.getPrimaryImageUrl()).isEqualTo("https://cdn/" + A2 + ".jpg");
        assertThat(item.getEndTime()).isEqualTo(OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        assertThat(item.getLastBidAt()).isNotNull();
    }

    @Test
    void sortsByEndTimeSoonestFirst() {
        when(bidRepository.findAuctionBidsByBidder(any(), any()))
                .thenReturn(List.of(bid(A1, "10.00"), bid(A2, "10.00"), bid(A3, "10.00")));
        when(auctionBidGateway.activeSummaries(anyList())).thenReturn(List.of(
                summary(A1, ME, "10.00", "2026-10-19T10:00:00Z"),
                summary(A2, ME, "10.00", "2026-10-17T10:00:00Z"),
                summary(A3, ME, "10.00", "2026-10-18T10:00:00Z")));

        assertThat(service.myActiveBids(ME).getItems()).extracting(MyActiveBidResponse::getAuctionId)
                .containsExactly(A2, A3, A1);
    }

    @Test
    void dropsAuctionsThatAreNoLongerActive() {
        when(bidRepository.findAuctionBidsByBidder(any(), any()))
                .thenReturn(List.of(bid(A1, "10.00"), bid(A2, "10.00")));
        when(auctionBidGateway.activeSummaries(anyList()))
                .thenReturn(List.of(summary(A2, ME, "10.00", "2026-10-17T10:00:00Z")));

        MyActiveBidsResponse response = service.myActiveBids(ME);

        assertThat(response.getItems()).extracting(MyActiveBidResponse::getAuctionId).containsExactly(A2);
        assertThat(response.getCounts().getAll()).isEqualTo(1);
    }

    @Test
    void noBids_returnsEmptyWithoutCallingAuctionService() {
        when(bidRepository.findAuctionBidsByBidder(any(), any())).thenReturn(List.of());

        MyActiveBidsResponse response = service.myActiveBids(ME);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getCounts().getAll()).isZero();
        verifyNoInteractions(auctionBidGateway);
    }

    @Test
    void scansAtMost300RecentAuctions() {
        when(bidRepository.findAuctionBidsByBidder(any(), any())).thenReturn(List.of());

        service.myActiveBids(ME);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bidRepository).findAuctionBidsByBidder(any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(300);
    }

    @Test
    void returnsAtMost100Items() {
        List<UUID> auctionIds = IntStream.range(0, 120).mapToObj(i -> UUID.randomUUID()).toList();
        when(bidRepository.findAuctionBidsByBidder(any(), any()))
                .thenReturn(auctionIds.stream().map(id -> bid(id, "10.00")).toList());
        when(auctionBidGateway.activeSummaries(anyList())).thenReturn(
                auctionIds.stream().map(id -> summary(id, ME, "10.00", "2026-10-17T10:00:00Z")).toList());

        MyActiveBidsResponse response = service.myActiveBids(ME);

        assertThat(response.getItems()).hasSize(100);
        assertThat(response.getCounts().getAll()).isEqualTo(100);
    }

    @Test
    void auctionServiceFailure_propagates() {
        when(bidRepository.findAuctionBidsByBidder(any(), any())).thenReturn(List.of(bid(A1, "10.00")));
        when(auctionBidGateway.activeSummaries(anyList()))
                .thenThrow(new ServiceUnavailableException("Auction service is unavailable"));

        assertThatThrownBy(() -> service.myActiveBids(ME)).isInstanceOf(ServiceUnavailableException.class);
    }
}
