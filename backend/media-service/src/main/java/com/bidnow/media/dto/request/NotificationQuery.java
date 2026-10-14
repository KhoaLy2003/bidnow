package com.bidnow.media.dto.request;

import com.bidnow.media.domain.enums.NotificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/** Inbox paging and filters. Newest first; id breaks ties so pages are stable. */
@Data
public class NotificationQuery {

    @Min(value = 0, message = "page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "size must be between 1 and 100")
    @Max(value = 100, message = "size must be between 1 and 100")
    private int size = 20;

    /** true = only read, false = only unread, null = both. */
    private Boolean read;

    private List<NotificationType> types;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime to;

    @Size(max = 100, message = "search must be at most 100 characters")
    private String search;

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
