package com.bidnow.media.notification;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserNotificationPreferenceResponse;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.feign.UserIdsRequest;
import com.bidnow.media.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves user IDs to email address (identity-service) plus language and email opt-in (user-service), in
 * batches. A failing lookup degrades instead of throwing: no email for those users, or EN with opt-in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipientDirectory {

    /** user-service caps batch lookups at 100 IDs. */
    static final int CHUNK_SIZE = 100;

    private final IdentityServiceClient identityServiceClient;
    private final UserServiceClient userServiceClient;

    public Map<UUID, Recipient> resolve(Collection<UUID> userIds) {
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> emails = new HashMap<>();
        Map<UUID, UserNotificationPreferenceResponse> preferences = new HashMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK_SIZE) {
            List<UUID> chunk = List.copyOf(ids.subList(from, Math.min(from + CHUNK_SIZE, ids.size())));
            emails.putAll(fetchEmails(chunk));
            fetchPreferences(chunk).forEach(p -> preferences.put(p.getUserId(), p));
        }

        Map<UUID, Recipient> recipients = new LinkedHashMap<>();
        for (UUID id : ids) {
            UserNotificationPreferenceResponse preference = preferences.get(id);
            recipients.put(id, new Recipient(
                    id,
                    emails.get(id),
                    NotificationLanguage.fromCode(preference == null ? null : preference.getLanguage()),
                    preference == null || !Boolean.FALSE.equals(preference.getEmailNotifications())));
        }
        return recipients;
    }

    private Map<UUID, String> fetchEmails(List<UUID> chunk) {
        try {
            BaseResponse<Map<UUID, String>> response = identityServiceClient.getEmailsByUserIds(chunk);
            return response == null || response.getData() == null ? Map.of() : response.getData();
        } catch (RuntimeException ex) {
            log.warn("Email lookup failed for {} users - their emails are skipped: {}", chunk.size(), ex.getMessage());
            return Map.of();
        }
    }

    private List<UserNotificationPreferenceResponse> fetchPreferences(List<UUID> chunk) {
        try {
            BaseResponse<List<UserNotificationPreferenceResponse>> response =
                    userServiceClient.getNotificationPreferences(new UserIdsRequest(chunk));
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RuntimeException ex) {
            log.warn("Preference lookup failed for {} users - defaulting to EN with email opt-in: {}",
                    chunk.size(), ex.getMessage());
            return List.of();
        }
    }
}
