package ru.openapi.tokens.security;

import java.util.UUID;

/**
 * Raised when a token exceeds its rate limit during authentication.
 */
public class RateLimitExceededException extends RuntimeException {

    private final UUID tokenId;

    public RateLimitExceededException(UUID tokenId) {
        super("Rate limit exceeded for token " + tokenId);
        this.tokenId = tokenId;
    }

    public UUID getTokenId() {
        return tokenId;
    }
}
