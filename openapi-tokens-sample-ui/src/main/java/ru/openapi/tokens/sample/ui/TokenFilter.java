package ru.openapi.tokens.sample.ui;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.TokenStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Query object for the token list pages: free text search, owner filter (admin only)
 * and status filter. Applied in memory over the list provided by {@code ApiTokenService}
 * (the starter exposes no paged/query API).
 */
public record TokenFilter(String q, String owner, String status) {

    public static final String ANY_STATUS = "ALL";

    public static TokenFilter of(String q, String owner, String status) {
        return new TokenFilter(q, owner, normalizeStatus(status));
    }

    /**
     * Filters and sorts (newest first) the given tokens.
     */
    public List<ApiToken> apply(List<ApiToken> tokens) {
        return tokens.stream()
                .filter(this::matches)
                .sorted(Comparator.comparing(ApiToken::createdAt).reversed())
                .toList();
    }

    public boolean matches(ApiToken token) {
        return matchesText(token) && matchesOwner(token) && matchesStatus(token);
    }

    public String statusOrDefault() {
        return status;
    }

    private boolean matchesText(ApiToken token) {
        if (isBlank(q)) {
            return true;
        }
        final String needle = q.trim().toLowerCase(Locale.ROOT);
        return contains(token.name(), needle)
                || contains(token.credentials().prefix(), needle)
                || contains(token.description(), needle);
    }

    private boolean matchesOwner(ApiToken token) {
        if (isBlank(owner)) {
            return true;
        }
        return contains(token.ownerId(), owner.trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesStatus(ApiToken token) {
        if (isBlank(status) || ANY_STATUS.equalsIgnoreCase(status)) {
            return true;
        }
        return token.status().name().equalsIgnoreCase(status);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeStatus(String status) {
        if (isBlank(status)) {
            return ANY_STATUS;
        }
        final String candidate = status.trim().toUpperCase(Locale.ROOT);
        for (TokenStatus known : TokenStatus.values()) {
            if (known.name().equals(candidate)) {
                return candidate;
            }
        }
        return ANY_STATUS;
    }
}
