package com.bidnow.identity.bdd.config;

import com.bidnow.bdd.container.KafkaContainerSupport;
import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.bdd.wiremock.WireMockSupport;
import com.bidnow.identity.IdentityApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(
        classes = IdentityApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        KafkaContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.cloud.openfeign.client.config.user-service.url",
                WireMockSupport::baseUrl);
    }
}
