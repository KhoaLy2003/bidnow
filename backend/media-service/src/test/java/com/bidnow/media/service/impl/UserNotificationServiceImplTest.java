package com.bidnow.media.service.impl;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationServiceImplTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime EARLIER = NOW.minusHours(1);

    @Mock
    private NotificationRepository repository;

    private UserNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserNotificationServiceImpl(repository,
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    }

    private static Notification notification(LocalDateTime readAt) {
        Notification n = Notification.builder()
                .id(ID).userId(ALICE).type(NotificationType.AUCTION_WON).channel(NotificationChannel.IN_APP)
                .title("You won").message("You won Vintage Watch").dedupKey("AUCTION_WON:x").readAt(readAt)
                .build();
        n.setCreatedAt(EARLIER);
        return n;
    }

    private Notification stubFind(LocalDateTime readAt) {
        Notification n = notification(readAt);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_queriesTheCallersInboxNewestFirst() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(null))));

        PageResponse<NotificationResponse> page = service.list(ALICE, new NotificationQuery());

        assertThat(page.getData()).extracting(NotificationResponse::getId).containsExactly(ID);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void unreadCount_delegates() {
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(3L);

        assertThat(service.unreadCount(ALICE)).isEqualTo(3);
    }

    @Test
    void get_notFoundForCaller_throws() {
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(ID, ALICE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ALICE, ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_unread_marksRead() {
        Notification n = stubFind(null);

        NotificationResponse response = service.get(ALICE, ID);

        assertThat(n.getReadAt()).isEqualTo(NOW);
        assertThat(response.isRead()).isTrue();
    }

    @Test
    void get_alreadyRead_keepsReadAt() {
        Notification n = stubFind(EARLIER);

        service.get(ALICE, ID);

        assertThat(n.getReadAt()).isEqualTo(EARLIER);
    }

    @Test
    void markUnread_clearsReadAt() {
        Notification n = stubFind(EARLIER);

        NotificationResponse response = service.markUnread(ALICE, ID);

        assertThat(n.getReadAt()).isNull();
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void markAllRead_returnsUpdatedCount() {
        when(repository.markAllRead(ALICE, NOW)).thenReturn(4);

        assertThat(service.markAllRead(ALICE)).isEqualTo(4);
    }

    @Test
    void delete_softDeletes() {
        Notification n = stubFind(null);

        service.delete(ALICE, ID);

        assertThat(n.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteAll_andDeleteRead_returnDeletedCounts() {
        when(repository.softDeleteAll(ALICE, NOW)).thenReturn(3);
        when(repository.softDeleteRead(ALICE, NOW)).thenReturn(2);

        assertThat(service.deleteAll(ALICE)).isEqualTo(3);
        assertThat(service.deleteRead(ALICE)).isEqualTo(2);
    }
}
