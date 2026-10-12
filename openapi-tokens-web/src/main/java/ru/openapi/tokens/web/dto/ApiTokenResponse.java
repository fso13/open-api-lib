package ru.openapi.tokens.web.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ApiTokenResponse(
        UUID id,
        String name,
        String description,
        String prefix,
        String ownerId,
        String tenantId,
        String status,
        String effectiveStatus,
        Set<String> scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Integer slidingTtlSeconds,
        Integer rateLimitRequests,
        Integer rateLimitWindowSeconds,
        String createdBy,
        Instant createdAt,
        Instant revokedAt
) {
}
