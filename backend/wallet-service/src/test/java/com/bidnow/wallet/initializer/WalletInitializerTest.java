package com.bidnow.wallet.initializer;

import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletInitializerTest {

    @Mock
    private WalletService walletService;

    private WalletInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new WalletInitializer(walletService);
    }

    @Test
    void initPlatformWallet_delegatesToWalletServiceWithConfiguredUUID() {
        UUID platformUserId = UUID.randomUUID();
        ReflectionTestUtils.setField(initializer, "platformUserId", platformUserId.toString());

        initializer.initPlatformWallet();

        verify(walletService).createWalletIfAbsent(platformUserId);
    }
}
