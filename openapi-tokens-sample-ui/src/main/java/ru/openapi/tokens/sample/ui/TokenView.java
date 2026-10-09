package ru.openapi.tokens.sample.ui;

import java.util.UUID;

/**
 * View model for a single API token row/detail (display ready, no domain types leaked to templates).
 */
public record TokenView(
        UUID id,
        String name,
        String description,
        String prefix,
        String ownerId,
        String tenantId,
        String status,
        String statusLabel,
        String statusCss,
        String scopeLabel,
        String createdAtLabel,
        String expiresAtLabel,
        boolean expiringSoon,
        String lastUsedAtLabel,
        String revokedAtLabel,
        String slidingTtlLabel,
        String rateLimitLabel
) {
}
