package ru.openapi.tokens.token;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Usage statistics for a token.
 */
public record TokenUsageStats(
        UUID tokenId,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        Instant lastUsedAt
) {

    public TokenUsageStats {
        Objects.requireNonNull(tokenId, "tokenId");
    }
}
