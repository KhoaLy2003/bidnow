package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.response.AuctionBidSummaryResponse;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidSummaryServiceTest {

    private static final UUID A1 = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID WINNER = UUID.fromString("c0000000-0000-0000-0000-000000000009");

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @InjectMocks
    private AuctionBidSummaryService service;

    private static AuctionItem auction(UUID id, String title) {
        return AuctionItem.builder()
                .id(id).title(title).status(AuctionStatus.ACTIVE)
                .currentPrice(new BigDecimal("125.00")).currentWinnerId(WINNER).totalBids(4)
                .endTime(OffsetDateTime.parse("2026-10-20T12:00:00Z"))
                .build();
    }

    private static AuctionImage image(AuctionItem auction, String url, boolean primary, int order) {
        return AuctionImage.builder().auction(auction).imageUrl(url).isPrimary(primary).displayOrder(order).build();
    }

    @Test
    void returnsNothingAndSkipsTheDatabaseForNoIds() {
        assertThat(service.getActiveSummaries(List.of())).isEmpty();

        verifyNoInteractions(auctionItemRepository, auctionImageRepository);
    }

    @Test
    void mapsActiveAuctionsWithTheirPrimaryImage() {
        AuctionItem a1 = auction(A1, "Leica M3");
        when(auctionItemRepository.findByIdInAndStatusAndDeletedAtIsNull(List.of(A1), AuctionStatus.ACTIVE))
                .thenReturn(List.of(a1));
        when(auctionImageRepository.findByAuctionIdInOrderByDisplayOrderAsc(List.of(A1)))
                .thenReturn(List.of(image(a1, "https://cdn/first.jpg", false, 0), image(a1, "https://cdn/primary.jpg", true, 1)));

        List<AuctionBidSummaryResponse> result = service.getActiveSummaries(List.of(A1));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.getAuctionId()).isEqualTo(A1);
            assertThat(s.getTitle()).isEqualTo("Leica M3");
            assertThat(s.getPrimaryImageUrl()).isEqualTo("https://cdn/primary.jpg");
            assertThat(s.getCurrentPrice()).isEqualByComparingTo("125.00");
            assertThat(s.getCurrentWinnerId()).isEqualTo(WINNER);
            assertThat(s.getTotalBids()).isEqualTo(4);
            assertThat(s.getEndTime()).isEqualTo(OffsetDateTime.parse("2026-10-20T12:00:00Z"));
        });
    }

    @Test
    void fallsBackToTheFirstImageWhenNoneIsPrimary() {
        AuctionItem a1 = auction(A1, "Leica M3");
        when(auctionItemRepository.findByIdInAndStatusAndDeletedAtIsNull(any(), any())).thenReturn(List.of(a1));
        when(auctionImageRepository.findByAuctionIdInOrderByDisplayOrderAsc(any()))
                .thenReturn(List.of(image(a1, "https://cdn/first.jpg", false, 0), image(a1, "https://cdn/second.jpg", false, 1)));

        assertThat(service.getActiveSummaries(List.of(A1)).get(0).getPrimaryImageUrl()).isEqualTo("https://cdn/first.jpg");
    }

    @Test
    void leavesTheImageNullWhenTheAuctionHasNone() {
        when(auctionItemRepository.findByIdInAndStatusAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(auction(A2, "Rug")));
        when(auctionImageRepository.findByAuctionIdInOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        assertThat(service.getActiveSummaries(List.of(A2)).get(0).getPrimaryImageUrl()).isNull();
    }

    @Test
    void asksOnlyForActiveNonDeletedAuctionsAndDoesNotLoadImagesWhenNoneMatch() {
        when(auctionItemRepository.findByIdInAndStatusAndDeletedAtIsNull(List.of(A1, A2), AuctionStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.getActiveSummaries(List.of(A1, A2))).isEmpty();

        verify(auctionItemRepository).findByIdInAndStatusAndDeletedAtIsNull(List.of(A1, A2), AuctionStatus.ACTIVE);
        verifyNoInteractions(auctionImageRepository);
    }
}
