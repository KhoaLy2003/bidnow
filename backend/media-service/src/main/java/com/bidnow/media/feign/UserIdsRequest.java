package com.bidnow.media.feign;

import java.util.List;
import java.util.UUID;

/** Body of user-service batch lookups: {@code {"userIds": [...]}} (max 100). */
public record UserIdsRequest(List<UUID> userIds) {
}
