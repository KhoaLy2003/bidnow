package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.common.enums.AuditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAuditEventListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private WalletAuditEventListener listener;

    @Test
    void onAudit_sendsToAuditEventsKeyedByCorrelationId() {
        UUID correlationId = UUID.randomUUID();
        AuditLogEvent audit = AuditLogEvent.builder()
                .correlationId(correlationId).entityType("Wallet").entityId("w-1").action(AuditAction.ADMIN_ACTION)
                .build();
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        listener.onAudit(new AuditApplicationEvent(this, audit));

        verify(kafkaTemplate).send(eq("audit-events"), eq(correlationId.toString()), eq(audit));
    }

    @Test
    void onAudit_withoutCorrelationId_keysByEntityId() {
        AuditLogEvent audit = AuditLogEvent.builder()
                .entityType("Wallet").entityId("w-2").action(AuditAction.ADMIN_ACTION)
                .build();
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        listener.onAudit(new AuditApplicationEvent(this, audit));

        verify(kafkaTemplate).send(eq("audit-events"), eq("w-2"), eq(audit));
    }
}
