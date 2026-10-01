package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.domain.enums.NotificationStatus;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.kafka.UserNotificationPushPublisher;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.repository.NotificationInboxRepository;
import com.bidnow.media.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final Map<String, Object> VARS = Map.of("userName", "Alice");

    @Mock
    private NotificationInboxRepository inboxRepository;
    @Mock
    private RecipientDirectory recipientDirectory;
    @Mock
    private TemplateResolver templateResolver;
    @Mock
    private EmailService emailService;
    @Mock
    private UserNotificationPushPublisher pushPublisher;
    @Mock
    private UnreadCounter unreadCounter;

    private NotificationDispatcher dispatcher;
    private final NotificationTemplate template = NotificationTemplate.builder().name("AUCTION_WON_VI").build();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        dispatcher = new NotificationDispatcher(inboxRepository, recipientDirectory, templateResolver,
                emailService, pushPublisher, unreadCounter, clock);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static NotificationIntent intent(UUID userId, NotificationIntent.EmailSpec email) {
        return new NotificationIntent(userId, NotificationType.AUCTION_WON, "AUCTION_WON:" + AUCTION, AUCTION,
                "You won", "You won Vintage Watch", "/auctions/" + AUCTION, Map.of("amount", "105.00"), email);
    }

    private static NotificationIntent.EmailSpec email(boolean transactional) {
        return new NotificationIntent.EmailSpec("AUCTION_WON", VARS, transactional);
    }

    @Test
    void dispatch_newNotification_persistsNowAndEmailsAndPushesAfterCommit() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(unreadCounter.count(ALICE)).thenReturn(3L);
        when(recipientDirectory.resolve(Set.of(ALICE)))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.VI, true)));
        when(templateResolver.resolve("AUCTION_WON", NotificationLanguage.VI)).thenReturn(Optional.of(template));

        dispatcher.dispatch(intent(ALICE, email(false)));

        verifyNoInteractions(recipientDirectory, emailService, pushPublisher, unreadCounter);
        commit();

        ArgumentCaptor<Notification> row = ArgumentCaptor.forClass(Notification.class);
        verify(inboxRepository).insertIfAbsent(row.capture());
        assertThat(row.getValue().getUserId()).isEqualTo(ALICE);
        assertThat(row.getValue().getType()).isEqualTo(NotificationType.AUCTION_WON);
        assertThat(row.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(row.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getValue().getDedupKey()).isEqualTo("AUCTION_WON:" + AUCTION);
        assertThat(row.getValue().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        UUID notificationId = row.getValue().getId();
        assertThat(notificationId).isNotNull();

        verify(emailService).sendTemplateEmail(notificationId, "alice@example.com", template, VARS);

        ArgumentCaptor<UserNotificationMessage> pushed = ArgumentCaptor.forClass(UserNotificationMessage.class);
        verify(pushPublisher).publish(eq(ALICE), pushed.capture());
        assertThat(pushed.getValue().type()).isEqualTo(UserNotificationMessage.NOTIFICATION);
        assertThat(pushed.getValue().unreadCount()).isEqualTo(3);
        assertThat(pushed.getValue().notification().getId()).isEqualTo(notificationId);
        assertThat(pushed.getValue().notification().getType()).isEqualTo("AUCTION_WON");
        assertThat(pushed.getValue().notification().isRead()).isFalse();
        assertThat(pushed.getValue().notification().getMetadata()).containsEntry("amount", "105.00");
    }

    @Test
    void dispatch_duplicate_hasNoSideEffects() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(false);

        dispatcher.dispatch(intent(ALICE, email(false)));
        commit();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(unreadCounter, recipientDirectory, templateResolver, emailService, pushPublisher);
    }

    @Test
    void dispatch_withoutEmail_onlyPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(unreadCounter.count(ALICE)).thenReturn(1L);

        dispatcher.dispatch(intent(ALICE, null));
        commit();

        verifyNoInteractions(recipientDirectory, templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_optedOutUser_skipsNonTransactionalEmail() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, false)));

        dispatcher.dispatch(intent(ALICE, email(false)));
        commit();

        verifyNoInteractions(templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_optedOutUser_stillGetsTransactionalEmail() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, false)));
        when(templateResolver.resolve("AUCTION_WON", NotificationLanguage.EN)).thenReturn(Optional.of(template));

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verify(emailService).sendTemplateEmail(any(UUID.class), eq("alice@example.com"), eq(template), eq(VARS));
    }

    @Test
    void dispatch_recipientWithoutEmail_skipsEmailButPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of());

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verifyNoInteractions(templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_emailServiceThrows_stillPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true)));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.of(template));
        doThrow(new IllegalStateException("template broken"))
                .when(emailService).sendTemplateEmail(any(UUID.class), anyString(), any(), any());

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatchAll_resolvesRecipientsOnceForTheBatch() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(Set.of(ALICE, BOB))).thenReturn(Map.of());

        dispatcher.dispatchAll(List.of(intent(ALICE, email(true)), intent(BOB, email(true))));
        commit();

        verify(recipientDirectory).resolve(Set.of(ALICE, BOB));
        verify(pushPublisher).publish(eq(ALICE), any());
        verify(pushPublisher).publish(eq(BOB), any());
    }

    @Test
    void dispatchAll_mixedBatch_deliversOnlyTheNewRow() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(false, true);

        dispatcher.dispatchAll(List.of(intent(ALICE, null), intent(BOB, null)));
        commit();

        verify(pushPublisher).publish(eq(BOB), any());
        verify(pushPublisher, never()).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_pushThrows_nextDeliveryStillProceeds() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        doThrow(new IllegalStateException("kafka down")).when(pushPublisher).publish(eq(ALICE), any());

        dispatcher.dispatchAll(List.of(intent(ALICE, null), intent(BOB, null)));
        commit();

        verify(pushPublisher).publish(eq(BOB), any());
    }

    @Test
    void dispatch_templateMissing_skipsEmailButPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any()))
                .thenReturn(Map.of(ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true)));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.empty());

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verifyNoInteractions(emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    void dispatch_templateResolverThrows_otherDeliveriesStillPushed() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of(
                ALICE, new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true),
                BOB, new Recipient(BOB, "bob@example.com", NotificationLanguage.EN, true)));
        when(templateResolver.resolve(anyString(), any())).thenThrow(new IllegalStateException("db down"));

        dispatcher.dispatchAll(List.of(intent(ALICE, email(true)), intent(BOB, email(true))));
        commit();

        verify(pushPublisher).publish(eq(ALICE), any());
        verify(pushPublisher).publish(eq(BOB), any());
        verifyNoInteractions(emailService);
    }

    @Test
    void dispatch_recipientLookupThrows_stillPushes() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenThrow(new IllegalStateException("user-service down"));

        dispatcher.dispatch(intent(ALICE, email(true)));
        commit();

        verifyNoInteractions(templateResolver, emailService);
        verify(pushPublisher).publish(eq(ALICE), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_emailWithoutUserName_greetsRecipientByDisplayName() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of(ALICE,
                new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true, "Alice Smith")));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.of(template));
        NotificationIntent.EmailSpec spec = new NotificationIntent.EmailSpec("AUCTION_LOST", Map.of("auctionTitle", "Watch"), false);

        dispatcher.dispatch(intent(ALICE, spec));
        commit();

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplateEmail(any(UUID.class), eq("alice@example.com"), eq(template), variables.capture());
        assertThat(variables.getValue())
                .containsEntry("userName", "Alice Smith")
                .containsEntry("auctionTitle", "Watch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_unknownDisplayName_greetsThere() {
        when(inboxRepository.insertIfAbsent(any())).thenReturn(true);
        when(recipientDirectory.resolve(any())).thenReturn(Map.of(ALICE,
                new Recipient(ALICE, "alice@example.com", NotificationLanguage.EN, true)));
        when(templateResolver.resolve(anyString(), any())).thenReturn(Optional.of(template));
        NotificationIntent.EmailSpec spec = new NotificationIntent.EmailSpec("AUCTION_LOST", Map.of("auctionTitle", "Watch"), false);

        dispatcher.dispatch(intent(ALICE, spec));
        commit();

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplateEmail(any(UUID.class), eq("alice@example.com"), eq(template), variables.capture());
        assertThat(variables.getValue()).containsEntry("userName", "there");
    }
}
