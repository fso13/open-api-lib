package ru.openapi.tokens.token;

import java.util.Objects;

/**
 * Single permission scope attached to a token.
 */
public record TokenScope(String value) {

    public TokenScope {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
    }
}
