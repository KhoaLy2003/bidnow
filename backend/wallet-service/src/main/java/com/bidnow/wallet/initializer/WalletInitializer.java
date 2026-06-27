package com.bidnow.wallet.initializer;

import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletInitializer {

    private final WalletService walletService;

    @Value("${wallet.platform-user-id}")
    private String platformUserId;

    @EventListener(ApplicationReadyEvent.class)
    public void initPlatformWallet() {
        UUID id = UUID.fromString(platformUserId);
        log.info("Seeding platform wallet for userId={}", id);
        walletService.createWalletIfAbsent(id);
        log.info("Platform wallet seed complete");
    }
}
