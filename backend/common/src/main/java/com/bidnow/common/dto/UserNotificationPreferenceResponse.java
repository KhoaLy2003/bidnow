/*
 * BidNow Auction System
 */
package com.bidnow.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A user's notification preferences, for cross-service consumption")
public class UserNotificationPreferenceResponse {

    @Schema(description = "User UUID")
    private UUID userId;

    @Schema(description = "Preferred language code, e.g. en or vi")
    private String language;

    @Schema(description = "Whether the user accepts non-transactional emails")
    private Boolean emailNotifications;

    @Schema(description = "Display name, used to greet the user in emails")
    private String displayName;
}
