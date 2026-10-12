package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ApiToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for API tokens.
 */
public interface ApiTokenRepository {

    ApiToken save(ApiToken token);

    Optional<ApiToken> findById(UUID id);

    Optional<ApiToken> findByPrefix(String prefix);

    List<ApiToken> findByOwnerId(String ownerId);

    List<ApiToken> findAll();

    /**
     * Bounded page of tokens for admin listings. Default implementation slices {@link #findAll()}.
     */
    default List<ApiToken> findPage(int offset, int limit) {
        final List<ApiToken> all = findAll();
        final int from = Math.max(0, Math.min(offset, all.size()));
        final int to = Math.max(from, Math.min(from + Math.max(0, limit), all.size()));
        return List.copyOf(all.subList(from, to));
    }

    /**
     * Total number of stored tokens.
     */
    default long count() {
        return findAll().size();
    }

    long countByOwnerId(String ownerId);

    void deleteById(UUID id);

    /**
     * Quota counter: only tokens that still consume a quota slot (ACTIVE/BLOCKED).
     *
     * <p>Defaults to {@link #countByOwnerId(String)} for stores that do not distinguish statuses.</p>
     */
    default long countUsableByOwnerId(String ownerId) {
        return countByOwnerId(ownerId);
    }

    /**
     * Serializes concurrent token creation per owner so that the quota cannot be exceeded by a race.
     *
     * <p>The default implementation is a no-op; the JPA adapter takes a PostgreSQL advisory lock
     * that is released when the surrounding transaction ends.</p>
     */
    default void lockOwnerQuota(String ownerId) {
        // no-op
    }

    /**
     * Targeted {@code lastUsedAt} update that does not touch the rest of the row.
     *
     * <p>Implemented as a single {@code UPDATE ... WHERE status = 'ACTIVE'} so that a concurrent
     * revocation cannot be overwritten with a stale entity snapshot. The default implementation
     * keeps the legacy read-modify-write behaviour for custom stores.</p>
     *
     * @return {@code true} when an ACTIVE token was updated
     */
    default boolean touchLastUsed(UUID id, Instant lastUsedAt) {
        return findById(id)
                .filter(ApiToken::isUsable)
                .map(token -> {
                    save(token.touchLastUsed(lastUsedAt));
                    return true;
                })
                .orElse(false);
    }
}
