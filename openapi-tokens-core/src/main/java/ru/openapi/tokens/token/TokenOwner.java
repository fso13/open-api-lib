package ru.openapi.tokens.token;

import java.util.Objects;

/**
 * Resolved owner of the current security context.
 */
public record TokenOwner(String ownerId, String tenantId) {

    public TokenOwner {
        Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    public static TokenOwner of(String ownerId) {
        return new TokenOwner(ownerId, null);
    }
}
