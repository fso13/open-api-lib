package ru.openapi.tokens.sample.ui;

/**
 * View model for a single audit log entry.
 */
public record AuditEntryView(
        String createdAtLabel,
        String action,
        boolean success,
        String resultLabel,
        String requestLabel,
        Integer responseStatus,
        String ip,
        String userAgent
) {
}
