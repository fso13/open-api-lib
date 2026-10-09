package ru.openapi.tokens.token;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit log entry for token authentication / authorization outcomes.
 */
public record AuditEvent(
        UUID tokenId,
        String ownerId,
        String tenantId,
        String httpMethod,
        String endpoint,
        String action,
        boolean success,
        Integer responseStatus,
        String ip,
        String userAgent,
        Instant createdAt
) {

    public AuditEvent {
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
