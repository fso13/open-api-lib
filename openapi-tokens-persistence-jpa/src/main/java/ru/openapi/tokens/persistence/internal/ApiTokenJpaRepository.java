package ru.openapi.tokens.persistence.internal;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.openapi.tokens.token.TokenStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenJpaRepository extends JpaRepository<ApiTokenEntity, UUID> {

    @EntityGraph(attributePaths = "scopes")
    Optional<ApiTokenEntity> findByPrefix(String prefix);

    @EntityGraph(attributePaths = "scopes")
    List<ApiTokenEntity> findByOwnerId(String ownerId);

    @Override
    @EntityGraph(attributePaths = "scopes")
    Optional<ApiTokenEntity> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "scopes")
    List<ApiTokenEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "scopes")
    List<ApiTokenEntity> findAllById(Iterable<UUID> ids);

    long countByOwnerId(String ownerId);

    long countByOwnerIdAndStatusIn(String ownerId, Collection<TokenStatus> statuses);

    /**
     * Targeted {@code lastUsedAt} update: never rewrites status/revocation fields, so a concurrent
     * revoke cannot be undone by a stale snapshot.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiTokenEntity t
               SET t.lastUsedAt = :lastUsedAt
             WHERE t.id = :id
               AND t.status = ru.openapi.tokens.token.TokenStatus.ACTIVE
            """)
    int touchLastUsed(@Param("id") UUID id, @Param("lastUsedAt") Instant lastUsedAt);

    /**
     * PostgreSQL transaction-scoped advisory lock that serializes token creation per owner.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:ownerId AS text))::bigint) IS NULL",
            nativeQuery = true)
    boolean lockOwner(@Param("ownerId") String ownerId);
}
