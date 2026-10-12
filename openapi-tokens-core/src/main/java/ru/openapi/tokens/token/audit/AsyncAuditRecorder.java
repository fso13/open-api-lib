package ru.openapi.tokens.token.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.AuditRecorder;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Asynchronous {@link AuditRecorder}.
 *
 * <p>Both submission and execution failures are logged and never propagated: a saturated or shut
 * down audit executor must not break authentication.</p>
 */
public final class AsyncAuditRecorder implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditRecorder.class);

    private final AuditLogRepository auditLogRepository;
    private final Executor executor;

    public AsyncAuditRecorder(AuditLogRepository auditLogRepository, Executor executor) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            executor.execute(() -> append(event));
        } catch (RejectedExecutionException ex) {
            // Executor saturated or shut down: drop the event instead of failing the request.
            log.error("Audit executor rejected the event for token {}", event.tokenId(), ex);
        }
    }

    private void append(AuditEvent event) {
        try {
            auditLogRepository.append(event);
        } catch (RuntimeException ex) {
            log.error("Failed to append audit event for token {}", event.tokenId(), ex);
        }
    }
}
