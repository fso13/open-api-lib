package ru.openapi.tokens.token.scope;

/**
 * How token scopes are turned into project authorities.
 */
public enum ScopeMode {

    /** 1:1 — every token scope becomes an authority as is. */
    IDENTITY,

    /** 1:M — token scopes are expanded through configured/stored mappings. */
    MAPPED
}
