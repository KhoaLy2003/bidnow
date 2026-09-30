package com.bidnow.media.notification;

import com.bidnow.media.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadCounterTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private NotificationRepository repository;
    @InjectMocks
    private UnreadCounter counter;

    @Test
    void count_delegatesToRepository() {
        when(repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(ALICE)).thenReturn(7L);

        assertThat(counter.count(ALICE)).isEqualTo(7);
    }

    @Test
    void count_runsInItsOwnReadOnlyTransaction() throws NoSuchMethodException {
        Transactional tx = UnreadCounter.class.getMethod("count", UUID.class).getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx.readOnly()).isTrue();
    }
}
