package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private UserRegisteredEventConsumer consumer;

    @Test
    void consumeUserRegistered_delegatesToWalletService() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(userId)
                .email("test@example.com")
                .build();

        consumer.consumeUserRegistered(event);

        verify(walletService).createWalletIfAbsent(userId);
    }
}
