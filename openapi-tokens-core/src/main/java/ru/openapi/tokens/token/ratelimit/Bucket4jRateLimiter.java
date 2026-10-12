package ru.openapi.tokens.token.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.spi.RateLimiter;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RateLimiter} backed by Bucket4j.
 *
 * <p>Buckets are keyed by token id only, so changing a token policy does not silently reset its
 * counter. The map is bounded: when {@code maxBuckets} is exceeded, the least recently used
 * buckets are evicted (also bounding memory for revoked tokens).</p>
 */
public final class Bucket4jRateLimiter implements RateLimiter {

    public static final int DEFAULT_MAX_BUCKETS = 10_000;

    private final TimeMeter timeMeter;
    private final int maxBuckets;
    private final Map<String, Holder> buckets = new ConcurrentHashMap<>();

    public Bucket4jRateLimiter() {
        this(TimeMeter.SYSTEM_NANOTIME, DEFAULT_MAX_BUCKETS);
    }

    public Bucket4jRateLimiter(TimeMeter timeMeter) {
        this(timeMeter, DEFAULT_MAX_BUCKETS);
    }

    public Bucket4jRateLimiter(TimeMeter timeMeter, int maxBuckets) {
        this.timeMeter = Objects.requireNonNull(timeMeter, "timeMeter");
        this.maxBuckets = Math.max(1, maxBuckets);
    }

    @Override
    public boolean tryAcquire(UUID tokenId, RateLimitPolicy policy) {
        Objects.requireNonNull(tokenId, "tokenId");
        return tryAcquire(tokenId.toString(), policy);
    }

    @Override
    public boolean tryAcquire(String key, RateLimitPolicy policy) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(policy, "policy");
        if (!policy.isConfigured()) {
            return true;
        }
        final Holder holder = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.matches(policy)) {
                return new Holder(createBucket(policy), policy, timeMeter.currentTimeNanos());
            }
            existing.touch(timeMeter.currentTimeNanos());
            return existing;
        });
        evictIfNeeded();
        return holder.bucket().tryConsume(1);
    }

    @Override
    public boolean isExhausted(String key, RateLimitPolicy policy) {
        Objects.requireNonNull(key, "key");
        final Holder holder = buckets.get(key);
        if (holder == null || policy == null || !holder.matches(policy)) {
            return false;
        }
        return holder.bucket().getAvailableTokens() <= 0;
    }

    @Override
    public void reset(String key) {
        if (key != null) {
            buckets.remove(key);
        }
    }

    /**
     * @return current number of tracked buckets (mainly for tests)
     */
    public int bucketCount() {
        return buckets.size();
    }

    private void evictIfNeeded() {
        if (buckets.size() <= maxBuckets) {
            return;
        }
        final int toRemove = buckets.size() - maxBuckets;
        buckets.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessNanos()))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .forEach(buckets::remove);
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

    private static final class Holder {
        private final Bucket bucket;
        private final RateLimitPolicy policy;
        private volatile long lastAccessNanos;

        private Holder(Bucket bucket, RateLimitPolicy policy, long lastAccessNanos) {
            this.bucket = bucket;
            this.policy = policy;
            this.lastAccessNanos = lastAccessNanos;
        }

        private Bucket bucket() {
            return bucket;
        }

        private boolean matches(RateLimitPolicy other) {
            return policy.equals(other);
        }

        private void touch(long nanos) {
            this.lastAccessNanos = nanos;
        }

        private long lastAccessNanos() {
            return lastAccessNanos;
        }
    }
}
