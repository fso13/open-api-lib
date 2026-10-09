package ru.openapi.tokens.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenUsageStatsResponse(
        UUID tokenId,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        Instant lastUsedAt
) {
}
