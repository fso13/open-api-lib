package ru.openapi.tokens.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit log entry. Exposed instead of the internal domain record so that the REST contract is
 * stable and independent from the persistence model.
 */
public record AuditEventResponse(
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
}
