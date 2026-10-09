package ru.openapi.tokens.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Token expiry rules")
class TokenExpiryTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("Should be expired when absolute expiresAt is in the past")
    void shouldBeExpiredWhenAbsolutePast() {
        final TokenExpiry expiry = TokenExpiry.ofAbsolute(NOW.minusSeconds(1));

        assertThat(expiry.isExpired(CLOCK)).isTrue();
    }

    @Test
    @DisplayName("Should not be expired when absolute expiresAt is in the future")
    void shouldNotBeExpiredWhenAbsoluteFuture() {
        final TokenExpiry expiry = TokenExpiry.ofAbsolute(NOW.plusSeconds(60));

        assertThat(expiry.isExpired(CLOCK)).isFalse();
    }

    @Test
    @DisplayName("Should never expire when absolute TTL is absent")
    void shouldNeverExpireWithoutAbsolute() {
        final TokenExpiry expiry = TokenExpiry.none();

        assertThat(expiry.isExpired(CLOCK)).isFalse();
    }

    @Test
    @DisplayName("Should be expired when sliding window since lastUsedAt elapsed")
    void shouldBeExpiredWhenSlidingElapsed() {
        final TokenExpiry expiry = TokenExpiry.ofSliding(
                Duration.ofHours(1),
                NOW.minus(Duration.ofHours(2))
        );

        assertThat(expiry.isExpired(CLOCK)).isTrue();
    }

    @Test
    @DisplayName("Should not be expired when sliding window is still open")
    void shouldNotBeExpiredWhenSlidingOpen() {
        final TokenExpiry expiry = TokenExpiry.ofSliding(
                Duration.ofHours(1),
                NOW.minus(Duration.ofMinutes(30))
        );

        assertThat(expiry.isExpired(CLOCK)).isFalse();
    }

    @Test
    @DisplayName("Should use createdAt as lastUsedAt when sliding and never used")
    void shouldUseCreatedAtWhenNeverUsed() {
        final Instant createdAt = NOW.minus(Duration.ofMinutes(10));
        final TokenExpiry expiry = TokenExpiry.ofSliding(Duration.ofHours(1), null)
                .withCreatedAt(createdAt);

        assertThat(expiry.isExpired(CLOCK)).isFalse();
    }

    @Test
    @DisplayName("Should expire if either absolute or sliding condition fails")
    void shouldExpireIfEitherConditionFails() {
        final TokenExpiry expiry = TokenExpiry.of(
                NOW.plusSeconds(3600),
                Duration.ofMinutes(5),
                NOW.minus(Duration.ofMinutes(10))
        );

        assertThat(expiry.isExpired(CLOCK)).isTrue();
    }

    @Test
    @DisplayName("Should compute next lastUsedAt for sliding refresh")
    void shouldRefreshSlidingLastUsedAt() {
        final TokenExpiry expiry = TokenExpiry.ofSliding(Duration.ofHours(1), NOW.minusSeconds(30));

        assertThat(expiry.refreshLastUsedAt(CLOCK)).isEqualTo(NOW);
    }
}
