package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.TokenUsageStats;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for audit log entries.
 */
public interface AuditLogRepository {

    void append(AuditEvent event);

    List<AuditEvent> findByTokenId(UUID tokenId, int offset, int limit);

    TokenUsageStats usageStats(UUID tokenId);
}
