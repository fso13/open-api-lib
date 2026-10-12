package ru.openapi.tokens.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RateLimitPolicy")
class RateLimitPolicyTest {

    @Test
    @DisplayName("Should accept unlimited and complete policies")
    void shouldAcceptValidPolicies() {
        assertThat(RateLimitPolicy.unlimited().isConfigured()).isFalse();
        assertThat(RateLimitPolicy.of(10, 60).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("Should reject a half configured limit")
    void shouldRejectHalfConfigured() {
        assertThatThrownBy(() -> new RateLimitPolicy(10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
        assertThatThrownBy(() -> new RateLimitPolicy(null, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject non positive values that used to crash authentication")
    void shouldRejectNonPositive() {
        assertThatThrownBy(() -> new RateLimitPolicy(0, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requests");
        assertThatThrownBy(() -> new RateLimitPolicy(10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowSeconds");
        assertThatThrownBy(() -> new RateLimitPolicy(-1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should fail closed for a sliding TTL without an anchor")
    void shouldFailClosedWithoutAnchor() {
        final Clock clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        final TokenExpiry expiry = TokenExpiry.ofSliding(java.time.Duration.ofDays(1), null);

        assertThat(expiry.isExpired(clock)).isTrue();
    }

    @Test
    @DisplayName("Should derive the effective status of an expired active token")
    void shouldDeriveEffectiveStatus() {
        final Instant now = Instant.parse("2026-10-07T12:00:00Z");
        final ApiToken expired = ApiToken.builder()
                .id(java.util.UUID.randomUUID())
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash"))
                .ownerId("owner-1")
                .scopes(java.util.Set.of("read"))
                .expiry(TokenExpiry.ofAbsolute(now.minusSeconds(60)))
                .createdAt(now.minusSeconds(3600))
                .build();

        assertThat(expired.status()).isEqualTo(TokenStatus.ACTIVE);
        assertThat(expired.effectiveStatus(now)).isEqualTo(TokenStatus.EXPIRED);
        assertThat(expired.effectiveStatus(now.minusSeconds(120))).isEqualTo(TokenStatus.ACTIVE);

        final ApiToken revoked = expired.revoke(now.minusSeconds(30));
        assertThat(revoked.effectiveStatus(now)).isEqualTo(TokenStatus.REVOKED);
    }
}
