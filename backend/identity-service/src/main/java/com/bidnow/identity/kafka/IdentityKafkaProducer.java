package com.bidnow.identity.kafka;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityKafkaProducer {

    private static final String USER_REGISTERED_TOPIC = "user-registered-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegisteredEvent(UserRegisteredEvent event) {
        kafkaTemplate.send(USER_REGISTERED_TOPIC, event.getUserId().toString(), event);
        log.info("Published UserRegisteredEvent for user: {}", event.getEmail());
    }
}
