package com.bidnow.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Auction closure: closure jobs are scheduled {@code grace} after the auction's end time, because
 * JobRunr enqueues scheduled jobs up to one poll interval early and a closure job must never run
 * before {@code end_time} (it would defer to a job with its own ID and be skipped).
 */
@ConfigurationProperties("auction.closure")
public record ClosureProperties(long graceSeconds) {

    public Duration grace() {
        return Duration.ofSeconds(graceSeconds);
    }
}
