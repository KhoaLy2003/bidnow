package com.bidnow.wallet.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void retryBackOff_isThreeRetriesStartingAtOneSecondDoubling() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerConfig.retryBackOff();

        assertThat(backOff.getMaxRetries()).isEqualTo(3);
        assertThat(backOff.getInitialInterval()).isEqualTo(1_000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandler_isBuilt() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(template);

        assertThat(handler).isNotNull();
    }
}
