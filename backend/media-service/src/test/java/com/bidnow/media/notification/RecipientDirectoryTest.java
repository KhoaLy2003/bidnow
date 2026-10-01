package com.bidnow.media.notification;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserNotificationPreferenceResponse;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.feign.UserIdsRequest;
import com.bidnow.media.feign.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientDirectoryTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private IdentityServiceClient identityServiceClient;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private RecipientDirectory directory;

    private static UserNotificationPreferenceResponse pref(UUID userId, String language, Boolean email) {
        return UserNotificationPreferenceResponse.builder()
                .userId(userId).language(language).emailNotifications(email).build();
    }

    @Test
    void resolve_carriesDisplayName() {
        when(identityServiceClient.getEmailsByUserIds(anyList()))
                .thenReturn(BaseResponse.success(Map.of(ALICE, "alice@example.com")));
        when(userServiceClient.getNotificationPreferences(any())).thenReturn(BaseResponse.success(List.of(
                UserNotificationPreferenceResponse.builder().userId(ALICE).displayName("Alice").build())));

        assertThat(directory.resolve(List.of(ALICE)).get(ALICE).displayName()).isEqualTo("Alice");
    }

    @Test
    void resolve_combinesEmailsAndPreferences() {
        when(identityServiceClient.getEmailsByUserIds(anyList()))
                .thenReturn(BaseResponse.success(Map.of(ALICE, "alice@example.com", BOB, "bob@example.com")));
        when(userServiceClient.getNotificationPreferences(any()))
                .thenReturn(BaseResponse.success(List.of(pref(ALICE, "vi", false))));

        Map<UUID, Recipient> result = directory.resolve(List.of(ALICE, BOB, ALICE));

        assertThat(result).containsOnlyKeys(ALICE, BOB);
        assertThat(result.get(ALICE)).isEqualTo(new Recipient(ALICE, "alice@example.com", NotificationLanguage.VI, false));
        assertThat(result.get(BOB)).isEqualTo(new Recipient(BOB, "bob@example.com", NotificationLanguage.EN, true));
    }

    @Test
    void resolve_chunksLookupsBy100() {
        List<UUID> ids = IntStream.range(0, 150).mapToObj(i -> UUID.randomUUID()).toList();
        when(identityServiceClient.getEmailsByUserIds(anyList())).thenReturn(BaseResponse.success(Map.of()));
        when(userServiceClient.getNotificationPreferences(any())).thenReturn(BaseResponse.success(List.of()));

        Map<UUID, Recipient> result = directory.resolve(ids);

        assertThat(result).hasSize(150);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> emailChunks = ArgumentCaptor.forClass(List.class);
        verify(identityServiceClient, times(2)).getEmailsByUserIds(emailChunks.capture());
        assertThat(emailChunks.getAllValues()).extracting(List::size).containsExactly(100, 50);
        ArgumentCaptor<UserIdsRequest> prefChunks = ArgumentCaptor.forClass(UserIdsRequest.class);
        verify(userServiceClient, times(2)).getNotificationPreferences(prefChunks.capture());
        assertThat(prefChunks.getAllValues()).extracting(r -> r.userIds().size()).containsExactly(100, 50);
    }

    @Test
    void resolve_identityDown_keepsLanguageButHasNoEmail() {
        when(identityServiceClient.getEmailsByUserIds(anyList())).thenThrow(new RuntimeException("identity down"));
        when(userServiceClient.getNotificationPreferences(any()))
                .thenReturn(BaseResponse.success(List.of(pref(ALICE, "vi", true))));

        Recipient alice = directory.resolve(List.of(ALICE)).get(ALICE);

        assertThat(alice.email()).isNull();
        assertThat(alice.language()).isEqualTo(NotificationLanguage.VI);
    }

    @Test
    void resolve_userServiceDown_defaultsToEnglishAndOptIn() {
        when(identityServiceClient.getEmailsByUserIds(anyList()))
                .thenReturn(BaseResponse.success(Map.of(ALICE, "alice@example.com")));
        when(userServiceClient.getNotificationPreferences(any())).thenThrow(new RuntimeException("user-service down"));

        assertThat(directory.resolve(List.of(ALICE)).get(ALICE))
                .isEqualTo(new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true));
    }

    @Test
    void resolve_empty_makesNoCalls() {
        assertThat(directory.resolve(List.of())).isEmpty();
        verifyNoInteractions(identityServiceClient, userServiceClient);
    }

    @Test
    void unknown_hasNoEmailEnglishAndOptIn() {
        assertThat(Recipient.unknown(ALICE)).isEqualTo(new Recipient(ALICE, null, NotificationLanguage.EN, true));
    }
}
