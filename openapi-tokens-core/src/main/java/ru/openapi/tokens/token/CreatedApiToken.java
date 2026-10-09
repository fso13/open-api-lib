package ru.openapi.tokens.token;

import java.util.Objects;

/**
 * Result of token creation: domain aggregate + raw secret shown once.
 */
public record CreatedApiToken(ApiToken token, RawToken rawToken) {

    public CreatedApiToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(rawToken, "rawToken");
    }
}
