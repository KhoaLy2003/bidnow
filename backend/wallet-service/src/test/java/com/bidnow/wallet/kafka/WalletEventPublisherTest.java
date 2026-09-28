package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.wallet.domain.enums.RefundReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private WalletEventPublisher publisher;

    @Test
    void onDepositRefunded_sendsEventKeyedByUserId() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID auctionId = UUID.randomUUID();
        Instant refundedAt = Instant.parse("2026-09-28T10:00:00Z");
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        publisher.onDepositRefunded(new DepositRefundedApplicationEvent(this, userId, walletId, auctionId,
                new BigDecimal("50.00"), RefundReason.AUCTION_CANCELLED, refundedAt));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("deposit-refunded-topic"), eq(userId.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(DepositRefundedEvent.class);
        DepositRefundedEvent event = (DepositRefundedEvent) payload.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getWalletId()).isEqualTo(walletId);
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getReason()).isEqualTo("AUCTION_CANCELLED");
        assertThat(event.getRefundedAt()).isEqualTo(refundedAt);
    }
}
