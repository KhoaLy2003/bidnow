package com.bidnow.user.controller;

import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static com.bidnow.common.constant.ErrorCodes.NOT_FOUND;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserProfileController(userProfileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    // -------------------------------------------------------
    // GET /api/v1/users/internal/{userId}/summary
    // -------------------------------------------------------

    @Test
    void getUserSummary_knownUser_returns200WithSummary() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSummaryResponse summary = UserSummaryResponse.builder()
                .id(userId)
                .name("Alice Smith")
                .avatarUrl("https://cdn.example.com/alice.jpg")
                .build();

        when(userProfileService.getUserSummary(userId)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/users/internal/{userId}/summary", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.name").value("Alice Smith"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example.com/alice.jpg"));
    }

    @Test
    void getUserSummary_unknownUser_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getUserSummary(userId))
                .thenThrow(new NotFoundException("User profile not found", NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/internal/{userId}/summary", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserSummaries_returns200WithList() throws Exception {
        UUID alice = UUID.randomUUID();
        when(userProfileService.getUserSummaries(anyList())).thenReturn(List.of(
                UserSummaryResponse.builder().id(alice).name("Alice").build()));

        mockMvc.perform(post("/api/v1/users/internal/summaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + alice + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(alice.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Alice"));
    }

    @Test
    void getUserSummaries_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/internal/summaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userProfileService);
    }

    @Test
    void getUserSummaries_moreThan100Ids_returns400() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/api/v1/users/internal/summaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userProfileService);
    }
}
