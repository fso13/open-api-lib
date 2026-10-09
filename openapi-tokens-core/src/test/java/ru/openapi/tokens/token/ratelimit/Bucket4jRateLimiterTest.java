package ru.openapi.tokens.token.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.openapi.tokens.token.RateLimitPolicy;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bucket4jRateLimiter")
class Bucket4jRateLimiterTest {

    @Test
    @DisplayName("Should allow requests within limit")
    void shouldAllowWithinLimit() {
        final Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        final UUID tokenId = UUID.randomUUID();
        final RateLimitPolicy policy = new RateLimitPolicy(3, 60);

        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
    }

    @Test
    @DisplayName("Should deny requests over limit")
    void shouldDenyOverLimit() {
        final Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        final UUID tokenId = UUID.randomUUID();
        final RateLimitPolicy policy = new RateLimitPolicy(2, 60);

        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
        assertThat(limiter.tryAcquire(tokenId, policy)).isFalse();
    }

    @Test
    @DisplayName("Should isolate limits per token")
    void shouldIsolatePerToken() {
        final Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        final RateLimitPolicy policy = new RateLimitPolicy(1, 60);
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        assertThat(limiter.tryAcquire(first, policy)).isTrue();
        assertThat(limiter.tryAcquire(first, policy)).isFalse();
        assertThat(limiter.tryAcquire(second, policy)).isTrue();
    }

    @Test
    @DisplayName("Should allow again after window reset")
    void shouldAllowAfterWindowReset() {
        final MutableClock clock = new MutableClock();
        final Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(clock);
        final UUID tokenId = UUID.randomUUID();
        final RateLimitPolicy policy = new RateLimitPolicy(1, 10);

        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
        assertThat(limiter.tryAcquire(tokenId, policy)).isFalse();

        clock.advance(Duration.ofSeconds(11));

        assertThat(limiter.tryAcquire(tokenId, policy)).isTrue();
    }

    @Test
    @DisplayName("Should allow all when policy is unlimited")
    void shouldAllowWhenUnlimited() {
        final Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        final UUID tokenId = UUID.randomUUID();

        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire(tokenId, RateLimitPolicy.unlimited())).isTrue();
        }
    }
}
