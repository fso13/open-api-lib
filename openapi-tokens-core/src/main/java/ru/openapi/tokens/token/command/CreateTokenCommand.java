package ru.openapi.tokens.token.command;

import ru.openapi.tokens.token.RateLimitPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable command to create a new API token.
 */
public record CreateTokenCommand(
        String name,
        String description,
        Set<String> scopes,
        Instant expiresAt,
        Duration slidingTtl,
        RateLimitPolicy rateLimit,
        String tenantId
) {

    public CreateTokenCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scopes, "scopes");
    }

    public static CreateTokenCommand of(String name, Set<String> scopes) {
        return new CreateTokenCommand(name, null, scopes, null, null, RateLimitPolicy.unlimited(), null);
    }
}
