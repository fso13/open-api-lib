package ru.openapi.tokens.token.audit;

import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.spi.AuditRecorder;

/**
 * Discards audit events. Used when {@code openapi.tokens.audit.enabled=false}.
 */
public final class NoOpAuditRecorder implements AuditRecorder {

    @Override
    public void record(AuditEvent event) {
        // auditing disabled
    }
}
