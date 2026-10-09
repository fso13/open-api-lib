package ru.openapi.tokens.token;

/**
 * Lifecycle status of an API token.
 */
public enum TokenStatus {
    ACTIVE,
    REVOKED,
    EXPIRED,
    BLOCKED
}
