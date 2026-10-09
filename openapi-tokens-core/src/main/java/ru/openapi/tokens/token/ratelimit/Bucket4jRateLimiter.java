package ru.openapi.tokens.token.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.spi.RateLimiter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RateLimiter} backed by Bucket4j.
 */
public final class Bucket4jRateLimiter implements RateLimiter {

    private final TimeMeter timeMeter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket4jRateLimiter() {
        this(TimeMeter.SYSTEM_NANOTIME);
    }

    public Bucket4jRateLimiter(TimeMeter timeMeter) {
        this.timeMeter = Objects.requireNonNull(timeMeter, "timeMeter");
    }

    @Override
    public boolean tryAcquire(UUID tokenId, RateLimitPolicy policy) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(policy, "policy");
        if (!policy.isConfigured()) {
            return true;
        }
        final String key = bucketKey(tokenId, policy);
        final Bucket bucket = buckets.computeIfAbsent(key, ignored -> createBucket(policy));
        return bucket.tryConsume(1);
    }

    private Bucket createBucket(RateLimitPolicy policy) {
        final Bandwidth limit = Bandwidth.builder()
                .capacity(policy.requests())
                .refillGreedy(policy.requests(), Duration.ofSeconds(policy.windowSeconds()))
                .build();
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit)
                .build();
    }

    private static String bucketKey(UUID tokenId, RateLimitPolicy policy) {
        return tokenId + ":" + policy.requests() + ":" + policy.windowSeconds();
    }
}
