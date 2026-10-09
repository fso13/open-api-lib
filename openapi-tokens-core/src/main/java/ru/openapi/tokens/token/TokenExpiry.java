package ru.openapi.tokens.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Absolute and/or sliding TTL policy for an API token.
 */
public final class TokenExpiry {

    private final Instant expiresAt;
    private final Duration slidingTtl;
    private final Instant lastUsedAt;
    private final Instant createdAt;

    private TokenExpiry(Instant expiresAt, Duration slidingTtl, Instant lastUsedAt, Instant createdAt) {
        this.expiresAt = expiresAt;
        this.slidingTtl = slidingTtl;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
    }

    public static TokenExpiry none() {
        return new TokenExpiry(null, null, null, null);
    }

    public static TokenExpiry ofAbsolute(Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt");
        return new TokenExpiry(expiresAt, null, null, null);
    }

    public static TokenExpiry ofSliding(Duration slidingTtl, Instant lastUsedAt) {
        Objects.requireNonNull(slidingTtl, "slidingTtl");
        if (slidingTtl.isNegative() || slidingTtl.isZero()) {
            throw new IllegalArgumentException("slidingTtl must be positive");
        }
        return new TokenExpiry(null, slidingTtl, lastUsedAt, null);
    }

    public static TokenExpiry of(Instant expiresAt, Duration slidingTtl, Instant lastUsedAt) {
        if (slidingTtl != null && (slidingTtl.isNegative() || slidingTtl.isZero())) {
            throw new IllegalArgumentException("slidingTtl must be positive");
        }
        return new TokenExpiry(expiresAt, slidingTtl, lastUsedAt, null);
    }

    public TokenExpiry withCreatedAt(Instant createdAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        return new TokenExpiry(expiresAt, slidingTtl, lastUsedAt, createdAt);
    }

    public TokenExpiry withLastUsedAt(Instant lastUsedAt) {
        return new TokenExpiry(expiresAt, slidingTtl, lastUsedAt, createdAt);
    }

    public boolean isExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        final Instant now = clock.instant();
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return true;
        }
        if (slidingTtl != null) {
            final Instant anchor = lastUsedAt != null ? lastUsedAt : createdAt;
            if (anchor == null) {
                return false;
            }
            return !now.isBefore(anchor.plus(slidingTtl));
        }
        return false;
    }

    public Instant refreshLastUsedAt(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return clock.instant();
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Duration slidingTtl() {
        return slidingTtl;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Integer slidingTtlSeconds() {
        return slidingTtl == null ? null : Math.toIntExact(slidingTtl.toSeconds());
    }
}
