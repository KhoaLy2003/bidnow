package com.bidnow.auction.kafka;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuctionKafkaProducer producer;

    @Test
    void publishAuctionExtended_sendsToExtendedTopicKeyedByAuction() {
        Instant newEnd = Instant.now();
        AuctionExtendedEvent event = AuctionExtendedEvent.builder()
                .auctionId(UUID.randomUUID())
                .newEndTime(newEnd)
                .build();
        when(kafkaTemplate.send("auction-extended-topic", event.getAuctionId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishAuctionExtended(event);

        verify(kafkaTemplate).send("auction-extended-topic", event.getAuctionId().toString(), event);
    }

    @Test
    void publishAuctionExtended_asyncFailureIsLoggedNotThrown() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuctionKafkaProducer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Instant newEnd = Instant.now();
            AuctionExtendedEvent event = AuctionExtendedEvent.builder()
                    .auctionId(UUID.randomUUID())
                    .newEndTime(newEnd)
                    .build();
            CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new KafkaException("down"));
            when(kafkaTemplate.send("auction-extended-topic", event.getAuctionId().toString(), event)).thenReturn(failed);

            assertThatCode(() -> producer.publishAuctionExtended(event)).doesNotThrowAnyException();

            assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().startsWith("CRITICAL: Failed to publish AuctionExtendedEvent")
                    && e.getThrowableProxy() != null);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
