package ru.openapi.tokens.token.scope;

/**
 * How strictly scopes are validated when a token is created.
 */
public enum ScopeValidationMode {

    /** No validation at all (backward compatibility / tests only). */
    OFF,

    /** Only {@code deny-patterns} are rejected. */
    DENY_LIST,

    /** Denied patterns + membership in the {@link ru.openapi.tokens.token.spi.ScopeCatalog}. */
    CATALOG,

    /** {@code CATALOG} + "never delegate more than you hold yourself". */
    STRICT
}
