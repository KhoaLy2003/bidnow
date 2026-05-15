package com.bidnow.media.repository;

import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID>, JpaSpecificationExecutor<NotificationTemplate> {
    Optional<NotificationTemplate> findByNameAndLanguageAndActiveTrue(String name, NotificationLanguage language);
}
