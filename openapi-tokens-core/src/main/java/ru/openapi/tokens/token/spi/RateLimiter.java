package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.RateLimitPolicy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Rate limiting port.
 *
 * <p>Two kinds of buckets are used by the library: the per-token bucket
 * ({@link #tryAcquire(UUID, RateLimitPolicy)}) and the pre-authentication bucket keyed by an
 * arbitrary string ({@link #tryAcquire(String, RateLimitPolicy)}), used to throttle failed
 * authentication attempts before an expensive hash verification happens.</p>
 */
public interface RateLimiter {

    /**
     * @return {@code true} if the request is allowed, {@code false} if denied
     */
    boolean tryAcquire(UUID tokenId, RateLimitPolicy policy);

    /**
     * Key-based variant. The default implementation maps the key to a name-based UUID so that
     * existing implementations keep working.
     *
     * @return {@code true} if the request is allowed, {@code false} if denied
     */
    default boolean tryAcquire(String key, RateLimitPolicy policy) {
        return tryAcquire(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), policy);
    }

    /**
     * Non-consuming check used by the pre-authentication throttle: rejects an attempt whose failure
     * budget is already exhausted without spending a token on the (possibly legitimate) request.
     *
     * @return {@code true} when the bucket has no tokens left
     */
    default boolean isExhausted(String key, RateLimitPolicy policy) {
        return false;
    }

    /**
     * Drops the state of one bucket (for example after a token is revoked). No-op by default.
     */
    default void reset(String key) {
        // no-op
    }
}
