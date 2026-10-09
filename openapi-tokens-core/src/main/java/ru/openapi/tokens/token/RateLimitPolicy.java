package ru.openapi.tokens.token;

/**
 * Optional per-token rate limit. Null fields mean "use global defaults / unlimited".
 */
public record RateLimitPolicy(Integer requests, Integer windowSeconds) {

    public static RateLimitPolicy unlimited() {
        return new RateLimitPolicy(null, null);
    }

    public boolean isConfigured() {
        return requests != null && windowSeconds != null;
    }
}
