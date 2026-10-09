package ru.openapi.tokens.token;

import java.util.Objects;

/**
 * Persisted token credentials: public prefix + one-way hash of the secret.
 */
public record TokenCredentials(String prefix, String tokenHash) {

    public TokenCredentials {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(tokenHash, "tokenHash");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
    }
}
