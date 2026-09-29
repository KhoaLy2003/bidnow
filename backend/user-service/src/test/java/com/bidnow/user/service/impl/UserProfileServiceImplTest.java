package com.bidnow.user.service.impl;

import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.user.domain.entity.UserProfile;
import com.bidnow.user.mapper.UserProfileMapper;
import com.bidnow.user.repository.UserPreferencesRepository;
import com.bidnow.user.repository.UserProfileRepository;
import com.bidnow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserPreferencesRepository userPreferencesRepository;
    @Mock
    private UserProfileMapper userProfileMapper;

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    // -------------------------------------------------------
    // getUserSummary
    // -------------------------------------------------------

    @Test
    void getUserSummary_profileExists_returnsMappedSummary() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .displayName("Alice Smith")
                .avatarUrl("https://cdn.example.com/alice.jpg")
                .build();

        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UserSummaryResponse result = userProfileService.getUserSummary(userId);

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getName()).isEqualTo("Alice Smith");
        assertThat(result.getAvatarUrl()).isEqualTo("https://cdn.example.com/alice.jpg");
    }

    @Test
    void getUserSummary_nullDisplayName_nameIsNull() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .displayName(null)
                .avatarUrl(null)
                .build();

        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UserSummaryResponse result = userProfileService.getUserSummary(userId);

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getName()).isNull();
        assertThat(result.getAvatarUrl()).isNull();
    }

    @Test
    void getUserSummary_profileNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getUserSummary(userId))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------
    // createUserProfile
    // -------------------------------------------------------

    @Test
    void createUserProfile_withDisplayName_persistsDisplayName() {
        UUID userId = UUID.randomUUID();
        CreateUserProfileRequest request = CreateUserProfileRequest.builder()
                .userId(userId)
                .email("alice@example.com")
                .displayName("Alice Smith")
                .build();
        UserProfile savedProfile = UserProfile.builder()
                .userId(userId)
                .displayName("Alice Smith")
                .build();
        when(userProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

        userProfileService.createUserProfile(request);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Alice Smith");
    }

    @Test
    void createUserProfile_withNullDisplayName_persistsNullDisplayName() {
        UUID userId = UUID.randomUUID();
        CreateUserProfileRequest request = CreateUserProfileRequest.builder()
                .userId(userId)
                .email("bob@example.com")
                .displayName(null)
                .build();
        UserProfile savedProfile = UserProfile.builder()
                .userId(userId)
                .displayName(null)
                .build();
        when(userProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

        userProfileService.createUserProfile(request);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isNull();
    }

    @Test
    void getUserSummaries_returnsOnlyExistingUsersWithOneQuery() {
        UUID alice = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of(
                UserProfile.builder().userId(alice).displayName("Alice").avatarUrl("https://cdn/a.png").build()));

        List<UserSummaryResponse> result = userProfileService.getUserSummaries(List.of(alice, unknown, alice));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(alice);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        assertThat(result.get(0).getAvatarUrl()).isEqualTo("https://cdn/a.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userProfileRepository).findByUserIdIn(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(alice, unknown);
    }
}
