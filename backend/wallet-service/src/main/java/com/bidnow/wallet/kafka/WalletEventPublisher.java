package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.DepositReceivedEvent;
import com.bidnow.common.dto.event.DepositRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventPublisher {

    private static final String DEPOSIT_RECEIVED_TOPIC = "deposit-received-topic";
    private static final String DEPOSIT_REFUNDED_TOPIC = "deposit-refunded-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepositCompleted(DepositCompletedApplicationEvent event) {
        DepositReceivedEvent kafkaEvent = DepositReceivedEvent.builder()
                .userId(event.getUserId())
                .walletId(event.getWalletId())
                .amount(event.getAmount())
                .newBalance(event.getNewBalance())
                .build();
        kafkaTemplate.send(DEPOSIT_RECEIVED_TOPIC, event.getUserId().toString(), kafkaEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish DepositReceivedEvent for userId={}", event.getUserId(), ex);
                    } else {
                        log.info("Published DepositReceivedEvent for userId={}", event.getUserId());
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepositRefunded(DepositRefundedApplicationEvent event) {
        DepositRefundedEvent kafkaEvent = DepositRefundedEvent.builder()
                .userId(event.getUserId())
                .walletId(event.getWalletId())
                .auctionId(event.getAuctionId())
                .amount(event.getAmount())
                .reason(event.getReason().name())
                .refundedAt(event.getRefundedAt())
                .build();
        kafkaTemplate.send(DEPOSIT_REFUNDED_TOPIC, event.getUserId().toString(), kafkaEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish DepositRefundedEvent for userId={}, auctionId={}",
                                event.getUserId(), event.getAuctionId(), ex);
                    } else {
                        log.info("Published DepositRefundedEvent for userId={}, auctionId={}",
                                event.getUserId(), event.getAuctionId());
                    }
                });
    }
}
