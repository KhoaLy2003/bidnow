package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionActivationServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;
    @Mock
    private AuctionClosureService closureService;

    @InjectMocks
    private AuctionActivationService activationService;

    private AuctionItem scheduled(UUID id) {
        return AuctionItem.builder()
                .id(id)
                .sellerId(UUID.randomUUID())
                .title("t")
                .startingPrice(BigDecimal.TEN)
                .status(AuctionStatus.SCHEDULED)
                .startTime(OffsetDateTime.now().minusMinutes(1))
                .endTime(OffsetDateTime.now().plusDays(1))
                .build();
    }

    @Test
    void activate_readsWithRowLock_neverUnlockedRead() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = scheduled(id);
        when(auctionItemRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        activationService.activate(id);

        verify(auctionItemRepository).findByIdForUpdate(id);
        verify(auctionItemRepository, never()).findByIdAndDeletedAtIsNull(any());
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        verify(auctionItemRepository).save(auction);
        verify(closureService).scheduleClosureJob(any(), any());
    }

    @Test
    void activate_whenNotFound_skips() {
        UUID id = UUID.randomUUID();
        when(auctionItemRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        activationService.activate(id);

        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void activate_whenNotScheduled_skips() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = scheduled(id);
        auction.setStatus(AuctionStatus.CANCELLED);
        when(auctionItemRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        activationService.activate(id);

        verify(auctionItemRepository, never()).save(any());
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
    }
}
