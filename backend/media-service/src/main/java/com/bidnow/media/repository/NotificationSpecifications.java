package com.bidnow.media.repository;

import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.dto.request.NotificationQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** The caller's inbox: own, non-deleted notifications narrowed by the query's optional filters. */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    /**
     * @param zone zone of the stored (zone-less) created_at timestamps; from/to are converted into it
     */
    public static Specification<Notification> inbox(UUID userId, NotificationQuery query, ZoneId zone) {
        SpecificationBuilder<Notification> builder = SpecificationBuilder.<Notification>forEntity()
                .with("userId", SearchOperator.EQUAL, userId)
                .withIsNull("deletedAt");

        if (Boolean.TRUE.equals(query.getRead())) {
            builder.withIsNotNull("readAt");
        } else if (Boolean.FALSE.equals(query.getRead())) {
            builder.withIsNull("readAt");
        }
        builder.withInIfPresent("type", query.getTypes());
        builder.withIfPresent("createdAt", SearchOperator.GREATER_THAN_OR_EQUAL, toLocal(query.getFrom(), zone));
        builder.withIfPresent("createdAt", SearchOperator.LESS_THAN, toLocal(query.getTo(), zone));

        if (StringUtils.hasText(query.getSearch())) {
            String pattern = "%" + query.getSearch().trim().toLowerCase() + "%";
            builder.orGroup(or -> or.withLike("title", pattern).withLike("message", pattern));
        }
        return builder.build();
    }

    private static LocalDateTime toLocal(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toLocalDateTime();
    }
}
