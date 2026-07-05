package com.bidnow.wallet.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Loggable
public class UserRegisteredEventConsumer {

    private final WalletService walletService;

    @Transactional
    @KafkaListener(topics = "user-registered-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for userId={}", event.getUserId());
        walletService.createWalletIfAbsent(event.getUserId());
    }
}
