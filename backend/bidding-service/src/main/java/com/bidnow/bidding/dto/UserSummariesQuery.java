package com.bidnow.bidding.dto;

import java.util.List;
import java.util.UUID;

/** Body of user-service's internal POST /api/v1/users/internal/summaries (max 100 ids). */
public record UserSummariesQuery(List<UUID> userIds) {
}
