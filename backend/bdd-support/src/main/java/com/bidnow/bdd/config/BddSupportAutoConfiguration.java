package com.bidnow.bdd.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration that registers BDD support beans
 * (BddRestClient, ScenarioContext) into any Spring Boot test context
 * that has bdd-support on the classpath.
 */
@Configuration
@ComponentScan(basePackages = "com.bidnow.bdd")
public class BddSupportAutoConfiguration {
}
