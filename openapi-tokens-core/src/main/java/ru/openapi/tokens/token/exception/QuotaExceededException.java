package ru.openapi.tokens.token.exception;

/**
 * Raised when an owner already holds the maximum number of tokens.
 */
public class QuotaExceededException extends RuntimeException {

    private final String ownerId;
    private final int maxTokens;

    public QuotaExceededException(String ownerId, int maxTokens) {
        super("Token quota exceeded for owner '%s': max %d".formatted(ownerId, maxTokens));
        this.ownerId = ownerId;
        this.maxTokens = maxTokens;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}
