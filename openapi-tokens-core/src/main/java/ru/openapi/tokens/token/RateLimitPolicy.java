package ru.openapi.tokens.token;

/**
 * Optional per-token rate limit. Both fields are {@code null} (unlimited) or both are positive.
 *
 * <p>A half-configured policy is rejected instead of silently meaning "unlimited": the previous
 * behaviour turned a partially filled request into a token without any limit.</p>
 */
public record RateLimitPolicy(Integer requests, Integer windowSeconds) {

    public RateLimitPolicy {
        if ((requests == null) != (windowSeconds == null)) {
            throw new IllegalArgumentException(
                    "rate limit requires both requests and windowSeconds, or neither"
            );
        }
        if (requests != null && requests <= 0) {
            throw new IllegalArgumentException("rate limit requests must be positive");
        }
        if (windowSeconds != null && windowSeconds <= 0) {
            throw new IllegalArgumentException("rate limit windowSeconds must be positive");
        }
    }

    public static RateLimitPolicy unlimited() {
        return new RateLimitPolicy(null, null);
    }

    public static RateLimitPolicy of(int requests, int windowSeconds) {
        return new RateLimitPolicy(requests, windowSeconds);
    }

    public boolean isConfigured() {
        return requests != null && windowSeconds != null;
    }
}
