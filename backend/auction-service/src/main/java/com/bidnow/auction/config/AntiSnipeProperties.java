package com.bidnow.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Anti-sniping: a bid with less than {@code window} left extends the auction by {@code extension}. */
@ConfigurationProperties("auction.anti-snipe")
public record AntiSnipeProperties(long windowSeconds, long extensionSeconds) {

    public Duration window() {
        return Duration.ofSeconds(windowSeconds);
    }

    public Duration extension() {
        return Duration.ofSeconds(extensionSeconds);
    }
}
