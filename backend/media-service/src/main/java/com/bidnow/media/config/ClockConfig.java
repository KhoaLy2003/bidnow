package com.bidnow.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** System default zone, matching BaseEntity's LocalDateTime.now() timestamps in media tables. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
