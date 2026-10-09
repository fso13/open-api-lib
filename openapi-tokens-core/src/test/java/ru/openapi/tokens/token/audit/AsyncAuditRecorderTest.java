package ru.openapi.tokens.token.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.spi.AuditLogRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncAuditRecorder")
class AsyncAuditRecorderTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Should record success and failure outcomes")
    void shouldRecordOutcomes() {
        final Executor sync = Runnable::run;
        final AsyncAuditRecorder recorder = new AsyncAuditRecorder(auditLogRepository, sync);

        recorder.record(event(true, 200));
        recorder.record(event(false, 401));
        recorder.record(event(false, 403));
        recorder.record(event(false, 429));

        verify(auditLogRepository, times(4)).append(any());
    }

    @Test
    @DisplayName("Should swallow repository failures")
    void shouldSwallowFailures() {
        doThrow(new RuntimeException("db down")).when(auditLogRepository).append(any());
        final AsyncAuditRecorder recorder = new AsyncAuditRecorder(auditLogRepository, Runnable::run);

        assertThatCode(() -> recorder.record(event(true, 200))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should dispatch asynchronously via executor")
    void shouldDispatchAsync() {
        final AtomicBoolean executed = new AtomicBoolean(false);
        final Executor executor = command -> {
            executed.set(true);
            command.run();
        };
        doAnswer(inv -> null).when(auditLogRepository).append(any());
        final AsyncAuditRecorder recorder = new AsyncAuditRecorder(auditLogRepository, executor);

        recorder.record(event(true, 200));

        assertThatCode(() -> verify(auditLogRepository).append(any())).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(executed).isTrue();
    }

    private static AuditEvent event(boolean success, int status) {
        return new AuditEvent(
                UUID.randomUUID(), "owner", null, "GET", "/api", "AUTH",
                success, status, "127.0.0.1", "test", Instant.parse("2026-10-07T12:00:00Z")
        );
    }
}
