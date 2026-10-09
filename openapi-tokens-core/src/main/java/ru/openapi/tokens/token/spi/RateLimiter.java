package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.RateLimitPolicy;

import java.util.UUID;

/**
 * Per-token rate limiting port.
 */
public interface RateLimiter {

    /**
     * @return {@code true} if the request is allowed, {@code false} if denied
     */
    boolean tryAcquire(UUID tokenId, RateLimitPolicy policy);
}
