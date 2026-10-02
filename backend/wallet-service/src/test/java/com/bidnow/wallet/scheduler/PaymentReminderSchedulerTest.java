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

import java.time.Duration;
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
class PaymentReminderSchedulerTest {

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    @Mock
    private PaymentService paymentService;

    private PaymentReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReminderScheduler(paymentHoldRepository, paymentService, 25, 24);
    }

    @Test
    void sendDueReminders_queriesTheReminderWindowWithBatchSize() {
        when(paymentHoldRepository.findDueForReminderAuctionIds(any(), any(), any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        scheduler.sendDueReminders();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> remindBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentHoldRepository).findDueForReminderAuctionIds(eq(PaymentHoldStatus.PENDING_PAYMENT),
                now.capture(), remindBefore.capture(), page.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
        assertThat(Duration.between(now.getValue(), remindBefore.getValue())).isEqualTo(Duration.ofHours(24));
        assertThat(page.getValue()).isEqualTo(PageRequest.of(0, 25));
        verifyNoInteractions(paymentService);
    }

    @Test
    void sendDueReminders_remindsEveryDueHold() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findDueForReminderAuctionIds(any(), any(), any(), any())).thenReturn(List.of(a, b));

        scheduler.sendDueReminders();

        verify(paymentService).sendPaymentReminder(a);
        verify(paymentService).sendPaymentReminder(b);
    }

    @Test
    void sendDueReminders_continuesAfterOneFailure() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findDueForReminderAuctionIds(any(), any(), any(), any())).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("db down")).when(paymentService).sendPaymentReminder(a);

        scheduler.sendDueReminders();

        verify(paymentService).sendPaymentReminder(b);
    }
}
