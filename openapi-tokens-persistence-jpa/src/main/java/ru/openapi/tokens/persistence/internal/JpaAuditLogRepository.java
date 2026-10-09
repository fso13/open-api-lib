package ru.openapi.tokens.persistence.internal;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.persistence.internal.AuditLogEntity;
import ru.openapi.tokens.persistence.internal.AuditLogJpaRepository;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.spi.AuditLogRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaAuditLogRepository implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;

    public JpaAuditLogRepository(AuditLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void append(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        final AuditLogEntity entity = new AuditLogEntity();
        entity.setTokenId(event.tokenId());
        entity.setOwnerId(event.ownerId());
        entity.setTenantId(event.tenantId());
        entity.setHttpMethod(event.httpMethod());
        entity.setEndpoint(event.endpoint());
        entity.setAction(event.action());
        entity.setSuccess(event.success());
        entity.setResponseStatus(event.responseStatus());
        entity.setIp(event.ip());
        entity.setUserAgent(event.userAgent());
        entity.setCreatedAt(event.createdAt());
        jpaRepository.save(entity);
    }

    @Override
    public List<AuditEvent> findByTokenId(UUID tokenId, int offset, int limit) {
        final int page = limit <= 0 ? 0 : offset / limit;
        final int size = limit <= 0 ? 20 : limit;
        return jpaRepository.findByTokenIdOrderByCreatedAtDesc(tokenId, PageRequest.of(page, size)).stream()
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
