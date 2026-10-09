package ru.openapi.tokens.sample.ui;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenExpiry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Test fixtures for the UI slices.
 */
public final class UiTestTokens {

    public static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private UiTestTokens() {
    }

    public static ApiToken active(String name, String owner) {
        return build(name, owner, null, null);
    }

    public static ApiToken blocked(String name, String owner) {
        return build(name, owner, null, null).block();
    }

    public static ApiToken revoked(String name, String owner) {
        return build(name, owner, null, null).revoke(NOW);
    }

    public static ApiToken expired(String name, String owner) {
        return build(name, owner, null, null).markExpired();
    }

    public static RawToken rawToken() {
        return new RawToken("abcd1234", "s3cr3tvalue");
    }

    private static ApiToken build(String name, String owner, Instant expiresAt, Duration slidingTtl) {
        return ApiToken.builder()
                .id(UUID.nameUUIDFromBytes((owner + ":" + name).getBytes()))
                .name(name)
                .description("created by " + owner)
                .credentials(new TokenCredentials("abcd1234", "$argon2id$hash"))
                .ownerId(owner)
                .tenantId("tenant-" + owner)
                .scopes(java.util.Set.of("payments:read"))
                .expiry(TokenExpiry.of(expiresAt, slidingTtl, null).withCreatedAt(NOW))
                .createdAt(NOW)
                .createdBy(owner)
                .build();
    }
}
