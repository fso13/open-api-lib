package ru.openapi.tokens.token.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.AuditRecorder;

import java.util.Objects;

/**
 * Writes audit events on the caller thread. Used when {@code openapi.tokens.audit.async=false}.
 *
 * <p>Failures are logged and never propagated: a broken audit sink must not break authentication.</p>
 */
public final class SyncAuditRecorder implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(SyncAuditRecorder.class);

    private final AuditLogRepository auditLogRepository;

    public SyncAuditRecorder(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
    }

    @Override
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            auditLogRepository.append(event);
        } catch (RuntimeException ex) {
            log.error("Failed to append audit event for token {}", event.tokenId(), ex);
        }
    }
}
