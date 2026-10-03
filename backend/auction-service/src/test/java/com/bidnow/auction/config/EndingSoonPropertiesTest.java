package com.bidnow.auction.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndingSoonPropertiesTest {

    private static EndingSoonProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("auction.ending-soon", EndingSoonProperties.class);
    }

    @Test
    void absent_defaultsToSixtyAndFifteenMinutes() {
        assertThat(bind(Map.of()).thresholdsMinutes()).containsExactly(60, 15);
    }

    @Test
    void configuredList_isBound() {
        assertThat(bind(Map.of("auction.ending-soon.thresholds-minutes", "30,5")).thresholdsMinutes())
                .containsExactly(30, 5);
    }

    @Test
    void emptyList_disablesTheFeature() {
        assertThat(new EndingSoonProperties(List.of()).thresholdsMinutes()).isEmpty();
    }

    @Test
    void nonPositiveOrNullOrDuplicateValues_failFast() {
        assertThatThrownBy(() -> new EndingSoonProperties(List.of(15, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> new EndingSoonProperties(Arrays.asList(15, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> new EndingSoonProperties(List.of(15, 15)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct");
    }
}
