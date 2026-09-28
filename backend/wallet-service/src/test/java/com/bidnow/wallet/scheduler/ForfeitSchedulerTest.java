package com.bidnow.wallet.scheduler;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForfeitSchedulerTest {

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    @Mock
    private PaymentService paymentService;

    private ForfeitScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ForfeitScheduler(paymentHoldRepository, paymentService, 25);
    }

    @Test
    void forfeitExpiredHolds_queriesPendingExpiredWithBatchSize() {
        when(paymentHoldRepository.findExpiredAuctionIds(any(), any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        scheduler.forfeitExpiredHolds();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentHoldRepository).findExpiredAuctionIds(eq(PaymentHoldStatus.PENDING_PAYMENT), now.capture(), page.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(0, 25));
        verifyNoInteractions(paymentService);
    }

    @Test
    void forfeitExpiredHolds_forfeitsEveryExpiredHold() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findExpiredAuctionIds(any(), any(), any())).thenReturn(List.of(a, b));

        scheduler.forfeitExpiredHolds();

        verify(paymentService).forfeitExpiredHold(a);
        verify(paymentService).forfeitExpiredHold(b);
    }

    @Test
    void forfeitExpiredHolds_continuesAfterOneFailure() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findExpiredAuctionIds(any(), any(), any())).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("db down")).when(paymentService).forfeitExpiredHold(a);

        scheduler.forfeitExpiredHolds();

        verify(paymentService).forfeitExpiredHold(b);
    }
}
