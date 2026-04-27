package com.bidnow.notification.repository;

import com.bidnow.notification.domain.entity.NotificationTemplate;
import com.bidnow.notification.domain.enums.NotificationLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByNameAndLanguageAndActiveTrue(String name, NotificationLanguage language);
}
