package com.bidnow.media.dto.response;

import com.bidnow.media.domain.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private String actionUrl;
    private UUID auctionId;
    private Map<String, Object> metadata;
    private boolean read;
    private OffsetDateTime createdAt;

    /**
     * Maps a stored notification; {@code read} is derived from {@code readAt}. {@code createdAt} is stored as
     * server-local LocalDateTime (BaseEntity); it is sent with the server zone's offset so browsers compute the
     * right instant.
     */
    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .auctionId(notification.getAuctionId())
                .metadata(notification.getMetadata())
                .read(notification.getReadAt() != null)
                .createdAt(notification.getCreatedAt() == null ? null
                        : notification.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime())
                .build();
    }
}
