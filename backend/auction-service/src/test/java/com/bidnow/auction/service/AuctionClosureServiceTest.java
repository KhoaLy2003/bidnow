package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosureServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;

    @InjectMocks
    private AuctionClosureService closureService;

    @Test
    void close_whenActiveWithBids_completesAuction() {
        UUID auctionId = UUID.randomUUID();
        UUID currentWinnerId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(2)
                .currentWinnerId(currentWinnerId)
                .currentPrice(new BigDecimal("300.00"))
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .build();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        assertThat(auction.getWinnerId()).isEqualTo(currentWinnerId);
        assertThat(auction.getCompletedAt()).isNotNull();
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionStatusHistory> historyCaptor = ArgumentCaptor.forClass(AuctionStatusHistory.class);
        verify(auctionStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo("ACTIVE");
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<AuctionEndedEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        verify(kafkaProducer).publishAuctionEnded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getWinnerId()).isEqualTo(currentWinnerId);
        assertThat(eventCaptor.getValue().getWinningBidAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        // TODO: update this assertion when the bidding-service provides loser data (see loserIds TODO in AuctionClosureService)
        assertThat(eventCaptor.getValue().getLoserIds()).isEmpty();
    }

    @Test
    void close_whenActiveWithNoBids_failsAuction() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .build();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(auction.getWinnerId()).isNull();
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionEndedEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        verify(kafkaProducer).publishAuctionEnded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getWinnerId()).isNull();
        assertThat(eventCaptor.getValue().getWinningBidAmount()).isNull();
        // TODO: update this assertion when the bidding-service provides loser data (see loserIds TODO in AuctionClosureService)
        assertThat(eventCaptor.getValue().getLoserIds()).isEmpty();
    }

    @Test
    void close_whenAuctionNotFound_skipsGracefully() {
        UUID auctionId = UUID.randomUUID();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.empty());

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).save(any());
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }

    @Test
    void close_whenAlreadyCompleted_isNoop() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.COMPLETED)
                .build();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).save(any());
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }
}
