package com.bidnow.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;

/**
 * "Ending soon" alerts: minutes before an auction's end time at which bidders are notified. A platform default
 * (roadmap Decision 7), not set per auction. An empty list disables the alerts; invalid values fail startup.
 */
@ConfigurationProperties("auction.ending-soon")
public record EndingSoonProperties(List<Integer> thresholdsMinutes) {

    public static final List<Integer> DEFAULT_THRESHOLDS_MINUTES = List.of(60, 15);

    public EndingSoonProperties {
        if (thresholdsMinutes == null) {
            thresholdsMinutes = DEFAULT_THRESHOLDS_MINUTES;
        }
        if (thresholdsMinutes.stream().anyMatch(m -> m == null || m <= 0)) {
            throw new IllegalArgumentException(
                    "auction.ending-soon.thresholds-minutes must be positive minutes: " + thresholdsMinutes);
        }
        if (new HashSet<>(thresholdsMinutes).size() != thresholdsMinutes.size()) {
            throw new IllegalArgumentException(
                    "auction.ending-soon.thresholds-minutes must be distinct: " + thresholdsMinutes);
        }
        thresholdsMinutes = List.copyOf(thresholdsMinutes);
    }
}
