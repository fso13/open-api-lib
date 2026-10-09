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
        Set<String> scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt
) {
}
