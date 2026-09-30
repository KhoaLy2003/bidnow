package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.Notification;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal boot configuration for JPA slice tests in this package (avoids MediaApplication's Feign/Eureka setup).
 * @DataJpaTest supplies the JPA/Liquibase/DataSource auto-configuration itself.
 */
@SpringBootConfiguration
@EntityScan(basePackageClasses = Notification.class)
@EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
class RepositoryTestConfig {
}
