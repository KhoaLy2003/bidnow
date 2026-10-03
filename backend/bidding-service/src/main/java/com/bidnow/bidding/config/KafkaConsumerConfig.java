package com.bidnow.bidding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retries failed records with exponential backoff (1s, 2s, 4s), then publishes them to
 * {@code <topic>.DLT}. Spring Boot applies this CommonErrorHandler to every bidding listener.
 */
@Configuration
public class KafkaConsumerConfig {

    static final int MAX_RETRIES = 3;
    static final long INITIAL_INTERVAL_MS = 1_000L;
    static final double MULTIPLIER = 2.0;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), retryBackOff());
    }

    static ExponentialBackOffWithMaxRetries retryBackOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        return backOff;
    }
}
