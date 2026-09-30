package com.bidnow.media.controller;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.exception.MediaExceptionHandler;
import com.bidnow.media.service.UserNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Mock
    private UserNotificationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new MediaExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private static NotificationResponse response(boolean read) {
        return NotificationResponse.builder().id(ID).type("AUCTION_WON").title("You won").read(read).build();
    }

    @Test
    void list_bindsFiltersAndReturnsPage() throws Exception {
        when(service.list(eq(ALICE), any())).thenReturn(PageResponse.<NotificationResponse>builder()
                .data(List.of(response(false)))
                .pagination(PaginationMeta.builder().page(0).limit(20).total(1).totalPages(1).build())
                .build());

        mockMvc.perform(get("/api/v1/notifications").header(USER_HEADER, ALICE)
                        .param("read", "false").param("types", "AUCTION_WON", "AUCTION_LOST")
                        .param("from", "2026-10-01T00:00:00Z").param("search", "watch").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data.data[0].read").value(false))
                .andExpect(jsonPath("$.data.pagination.total").value(1));

        ArgumentCaptor<NotificationQuery> query = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(service).list(eq(ALICE), query.capture());
        assertThat(query.getValue().getRead()).isFalse();
        assertThat(query.getValue().getTypes()).containsExactly(NotificationType.AUCTION_WON, NotificationType.AUCTION_LOST);
        assertThat(query.getValue().getFrom()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        assertThat(query.getValue().getSearch()).isEqualTo("watch");
        assertThat(query.getValue().getSize()).isEqualTo(50);
    }

    @Test
    void list_sizeOver100_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").header(USER_HEADER, ALICE).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.INVALID_INPUT));
        verifyNoInteractions(service);
    }

    @Test
    void list_unknownType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").header(USER_HEADER, ALICE).param("types", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.INVALID_INPUT));
        verifyNoInteractions(service);
    }

    @Test
    void list_withoutUserHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.UNAUTHORIZED));
        verifyNoInteractions(service);
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(service.unreadCount(ALICE)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(7));
    }

    @Test
    void get_returnsNotificationMarkedRead() throws Exception {
        when(service.get(ALICE, ID)).thenReturn(response(true));

        mockMvc.perform(get("/api/v1/notifications/{id}", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void get_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{id}", "not-a-uuid").header(USER_HEADER, ALICE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.INVALID_INPUT));
        verifyNoInteractions(service);
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.get(ALICE, ID)).thenThrow(new NotFoundException("Notification not found", ErrorCodes.NOT_FOUND));

        mockMvc.perform(get("/api/v1/notifications/{id}", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCodes.NOT_FOUND));
    }

    @Test
    void markRead_andMarkUnread() throws Exception {
        when(service.markRead(ALICE, ID)).thenReturn(response(true));
        when(service.markUnread(ALICE, ID)).thenReturn(response(false));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(put("/api/v1/notifications/{id}/unread", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(false));
    }

    @Test
    void markAllRead_returnsUpdatedCount() throws Exception {
        when(service.markAllRead(ALICE)).thenReturn(4);

        mockMvc.perform(put("/api/v1/notifications/mark-all-read").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(4));
    }

    @Test
    void delete_single() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", ID).header(USER_HEADER, ALICE))
                .andExpect(status().isOk());

        verify(service).delete(ALICE, ID);
    }

    @Test
    void deleteAll_andDeleteRead_returnUpdatedCounts() throws Exception {
        when(service.deleteAll(ALICE)).thenReturn(5);
        when(service.deleteRead(ALICE)).thenReturn(2);

        mockMvc.perform(delete("/api/v1/notifications/delete-all").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(5));
        mockMvc.perform(delete("/api/v1/notifications/delete-read").header(USER_HEADER, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(2));
    }
}
