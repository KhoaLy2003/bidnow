package com.bidnow.notification.domain.entity;

import com.bidnow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notif_user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "email_enabled")
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "push_enabled")
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "sms_enabled")
    @Builder.Default
    private boolean smsEnabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_preferences", columnDefinition = "jsonb")
    private Map<String, Map<String, Boolean>> typePreferences;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "quiet_hours_timezone", length = 50)
    private String quietHoursTimezone;
}
