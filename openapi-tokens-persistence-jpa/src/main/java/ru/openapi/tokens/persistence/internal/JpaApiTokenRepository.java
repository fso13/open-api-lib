package ru.openapi.tokens.persistence.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.spi.ApiTokenRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class JpaApiTokenRepository implements ApiTokenRepository {

    /**
     * Statuses that still occupy a quota slot; REVOKED/EXPIRED tokens release it.
     */
    private static final Set<TokenStatus> QUOTA_STATUSES = Set.of(TokenStatus.ACTIVE, TokenStatus.BLOCKED);

    private final ApiTokenJpaRepository jpaRepository;
    private final ApiTokenEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaApiTokenRepository(ApiTokenJpaRepository jpaRepository, ApiTokenEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ApiToken save(ApiToken token) {
        Objects.requireNonNull(token, "token");
        final ApiTokenEntity saved = jpaRepository.save(mapper.toEntity(token));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ApiToken> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ApiToken> findByPrefix(String prefix) {
        return jpaRepository.findByPrefix(prefix).map(mapper::toDomain);
    }

    @Override
    public List<ApiToken> findByOwnerId(String ownerId) {
        return jpaRepository.findByOwnerId(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ApiToken> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ApiToken> findPage(int offset, int limit) {
        final int size = Math.max(1, limit);
        final List<UUID> ids = entityManager.createQuery(
                        "SELECT t.id FROM ApiTokenEntity t ORDER BY t.createdAt DESC, t.id DESC", UUID.class)
                .setFirstResult(Math.max(0, offset))
                .setMaxResults(size)
                .getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // Two queries instead of N+1: ids first, then a single fetch with the scopes graph.
        final Map<UUID, ApiTokenEntity> byId = jpaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ApiTokenEntity::getId, entity -> entity));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public long countByOwnerId(String ownerId) {
        return jpaRepository.countByOwnerId(ownerId);
    }

    @Override
    public long countUsableByOwnerId(String ownerId) {
        return jpaRepository.countByOwnerIdAndStatusIn(ownerId, QUOTA_STATUSES);
    }

    @Override
    @Transactional
    public void lockOwnerQuota(String ownerId) {
        if (ownerId != null) {
            jpaRepository.lockOwner(ownerId);
        }
    }

    @Override
    @Transactional
    public boolean touchLastUsed(UUID id, Instant lastUsedAt) {
        return jpaRepository.touchLastUsed(id, lastUsedAt) > 0;
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
