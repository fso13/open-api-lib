package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.AuditEvent;

/**
 * Records authentication/authorization audit events (preferably asynchronously).
 */
public interface AuditRecorder {

    void record(AuditEvent event);
}
