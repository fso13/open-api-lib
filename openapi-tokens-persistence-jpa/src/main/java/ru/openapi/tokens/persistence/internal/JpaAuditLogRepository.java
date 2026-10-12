package ru.openapi.tokens.persistence.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.spi.AuditLogRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaAuditLogRepository implements AuditLogRepository {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 500;

    private static final int OWNER_ID_MAX = 128;
    private static final int TENANT_ID_MAX = 128;
    private static final int HTTP_METHOD_MAX = 16;
    private static final int ENDPOINT_MAX = 512;
    private static final int ACTION_MAX = 128;
    private static final int IP_MAX = 64;
    private static final int USER_AGENT_MAX = 512;

    private final AuditLogJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaAuditLogRepository(AuditLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void append(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        final AuditLogEntity entity = new AuditLogEntity();
        entity.setTokenId(event.tokenId());
        // Values come from HTTP headers/paths: trim them to the column widths instead of losing the
        // whole event to a DataIntegrityViolationException.
        entity.setOwnerId(truncate(event.ownerId(), OWNER_ID_MAX));
        entity.setTenantId(truncate(event.tenantId(), TENANT_ID_MAX));
        entity.setHttpMethod(truncate(event.httpMethod(), HTTP_METHOD_MAX));
        entity.setEndpoint(truncate(event.endpoint(), ENDPOINT_MAX));
        entity.setAction(truncate(event.action(), ACTION_MAX));
        entity.setSuccess(event.success());
        entity.setResponseStatus(event.responseStatus());
        entity.setIp(truncate(event.ip(), IP_MAX));
        entity.setUserAgent(truncate(event.userAgent(), USER_AGENT_MAX));
        entity.setCreatedAt(event.createdAt());
        jpaRepository.save(entity);
    }

    @Override
    public List<AuditEvent> findByTokenId(UUID tokenId, int offset, int limit) {
        final int effectiveOffset = Math.max(0, offset);
        final int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return entityManager
                .createQuery("""
                        SELECT a FROM AuditLogEntity a
                         WHERE a.tokenId = :tokenId
                         ORDER BY a.createdAt DESC, a.id DESC
                        """, AuditLogEntity.class)
                .setParameter("tokenId", tokenId)
                .setFirstResult(effectiveOffset)
                .setMaxResults(effectiveLimit)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public TokenUsageStats usageStats(UUID tokenId) {
        final long total = jpaRepository.countByTokenId(tokenId);
        final long successful = jpaRepository.countSuccessfulByTokenId(tokenId);
        final List<AuditLogEntity> latest = jpaRepository.findByTokenIdOrderByCreatedAtDesc(tokenId, PageRequest.of(0, 1));
        return new TokenUsageStats(
                tokenId,
                total,
                successful,
                total - successful,
                latest.isEmpty() ? null : latest.getFirst().getCreatedAt()
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private AuditEvent toDomain(AuditLogEntity entity) {
        return new AuditEvent(
                entity.getTokenId(),
                entity.getOwnerId(),
                entity.getTenantId(),
                entity.getHttpMethod(),
                entity.getEndpoint(),
                entity.getAction(),
                entity.isSuccess(),
                entity.getResponseStatus(),
                entity.getIp(),
                entity.getUserAgent(),
                entity.getCreatedAt()
        );
    }
}
