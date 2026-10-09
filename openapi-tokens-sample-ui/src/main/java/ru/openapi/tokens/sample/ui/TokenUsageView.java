package ru.openapi.tokens.sample.ui;

import java.util.UUID;

/**
 * View model for token usage statistics.
 */
public record TokenUsageView(
        UUID tokenId,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        String failureRateLabel,
        String lastUsedAtLabel
) {
}
