// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/config/CucumberSpringConfig.java
package com.bidnow.bidding.bdd.config;

import com.bidnow.bdd.container.KafkaContainerSupport;
import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.bdd.container.RedisContainerSupport;
import com.bidnow.bdd.wiremock.WireMockSupport;
import com.bidnow.bidding.BiddingApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(
        classes = BiddingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        RedisContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.cloud.openfeign.client.config.auction-service.url", WireMockSupport::baseUrl);
        KafkaContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.cloud.openfeign.client.config.wallet-service.url", WireMockSupport::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.user-service.url", WireMockSupport::baseUrl);
    }
}
