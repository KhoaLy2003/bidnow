package com.bidnow.media.notification;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.media.notification.handler.AuctionNotificationHandler;
import com.bidnow.media.notification.handler.PaymentNotificationHandler;
import com.bidnow.media.projection.AuctionLookup;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.service.impl.NotificationServiceImpl;
import liquibase.integration.spring.SpringLiquibase;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every email a handler sends must have active EN and VI templates that render completely with the handler's
 * variables plus the dispatcher-supplied userName. Needs Docker; run explicitly:
 * mvn -q -pl media-service -am test -Dtest=NotificationTemplatesPostgresIT -Dsurefire.failIfNoSpecifiedTests=false
 */
class NotificationTemplatesPostgresIT {

    private static final Pattern UNRESOLVED = Pattern.compile("\\{[A-Za-z]+}");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID LOSER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() throws Exception {
        PostgreSQLContainer<?> pg = PostgresContainerSupport.POSTGRES;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
    }

    /** Runs every handler path that sends email and collects the EmailSpecs it produced. */
    @SuppressWarnings("unchecked")
    private static List<NotificationIntent.EmailSpec> emailSpecsFromAllHandlers() {
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        AuctionLookup auctions = mock(AuctionLookup.class);
        when(auctions.title(any(), any())).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(WINNER, LOSER));
        NotificationLinks links = new NotificationLinks("http://localhost:3000");

        AuctionNotificationHandler auctionHandler = new AuctionNotificationHandler(dispatcher, auctions, links);
        PaymentNotificationHandler paymentHandler = new PaymentNotificationHandler(dispatcher, auctions, links);
        NotificationServiceImpl accountHandler = new NotificationServiceImpl(mock(EmailService.class), mock(TemplateResolver.class), dispatcher);
        ReflectionTestUtils.setField(accountHandler, "frontendBaseUrl", "http://localhost:3000");

        accountHandler.handleUserRegistered(UserRegisteredEvent.builder().userId(WINNER).email("a@example.com").firstName("Alice").build());
        auctionHandler.auctionCreated(AuctionCreatedEvent.builder().auctionId(AUCTION).sellerId(SELLER).title("Vintage Watch").build());
        auctionHandler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).sellerId(SELLER).winnerId(WINNER)
                .winningBidAmount(new BigDecimal("150")).build());
        auctionHandler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).build());
        for (String type : List.of("REQUIRED", "REMINDER_24H", "COMPLETED", "FAILED")) {
            paymentHandler.paymentEvent(PaymentEvent.builder().auctionId(AUCTION).userId(WINNER).sellerId(SELLER)
                    .paymentType(type).amount(new BigDecimal("1500")).depositAmount(new BigDecimal("150"))
                    .remaining(new BigDecimal("1350")).deadline(Instant.parse("2026-10-03T10:00:00Z")).build());
        }
        paymentHandler.depositRefunded(DepositRefundedEvent.builder().userId(LOSER).auctionId(AUCTION)
                .amount(new BigDecimal("150")).reason("AUCTION_LOST").build());

        List<NotificationIntent> intents = new ArrayList<>();
        ArgumentCaptor<NotificationIntent> single = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher, atLeast(0)).dispatch(single.capture());
        intents.addAll(single.getAllValues());
        ArgumentCaptor<List<NotificationIntent>> batches = ArgumentCaptor.forClass(List.class);
        verify(dispatcher, atLeast(0)).dispatchAll(batches.capture());
        batches.getAllValues().forEach(intents::addAll);

        return intents.stream().map(NotificationIntent::email).filter(java.util.Objects::nonNull).toList();
    }

    @Test
    void everyHandlerEmailHasCompleteEnAndViTemplates() {
        List<NotificationIntent.EmailSpec> specs = emailSpecsFromAllHandlers();
        assertThat(specs).extracting(NotificationIntent.EmailSpec::templateBaseName)
                .contains("WELCOME_EMAIL", "AUCTION_CREATED", "AUCTION_LOST", "AUCTION_CANCELLED",
                        "AUCTION_WON", "PAYMENT_SUCCESSFUL", "PAYMENT_FAILED", "SALE_PAYMENT_RECEIVED", "DEPOSIT_REFUNDED",
                        "PAYMENT_REMINDER_2");

        for (NotificationIntent.EmailSpec spec : specs) {
            Map<String, String> variables = new HashMap<>();
            spec.variables().forEach((key, value) -> variables.put(key, String.valueOf(value)));
            variables.putIfAbsent("userName", "Alice"); // the dispatcher always supplies userName
            StringSubstitutor substitutor = new StringSubstitutor(variables, "{", "}");

            for (String language : List.of("EN", "VI")) {
                String name = spec.templateBaseName() + "_" + language;
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT subject, body_html, body_text FROM media_notification_templates WHERE name = ? AND active", name);
                assertThat(rows).as("active template %s", name).hasSize(1);
                Map<String, Object> row = rows.get(0);
                for (String column : List.of("subject", "body_html", "body_text")) {
                    Object text = row.get(column);
                    if (text != null) {
                        String rendered = substitutor.replace(text.toString());
                        assertThat(UNRESOLVED.matcher(rendered).results().map(r -> r.group()).collect(Collectors.toList()))
                                .as("unresolved placeholders in %s.%s", name, column)
                                .isEmpty();
                    }
                }
            }
        }
    }

    @Test
    void noTemplateBodyTextContainsLiteralBackslashN() {
        // chr(92) is a backslash; avoids LIKE/E'' escaping ambiguity in the check itself
        Integer literal = jdbc.queryForObject(
                "SELECT count(*) FROM media_notification_templates WHERE position(chr(92) || 'n' in body_text) > 0",
                Integer.class);
        assertThat(literal).as("templates with a literal backslash-n in body_text").isZero();

        Integer viaLike = jdbc.queryForObject(
                "SELECT count(*) FROM media_notification_templates WHERE body_text LIKE E'%\\\\\\\\n%'", Integer.class);
        assertThat(viaLike).isZero();

        String welcome = jdbc.queryForObject(
                "SELECT body_text FROM media_notification_templates WHERE name = 'WELCOME_EMAIL_EN'", String.class);
        assertThat(welcome).contains("\n").startsWith("Hello {userName},\n\nWelcome");
    }
}
