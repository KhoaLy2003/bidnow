package com.bidnow.bdd.container;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class KafkaContainerSupport {

    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static {
        KAFKA.start();
    }

    public static Map<String, String> properties() {
        return Map.of("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }
}
