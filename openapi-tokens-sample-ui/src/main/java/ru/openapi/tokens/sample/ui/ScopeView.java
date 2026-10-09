package ru.openapi.tokens.sample.ui;

/**
 * View model for one scope catalog entry.
 */
public record ScopeView(
        String scope,
        String description,
        String authoritiesLabel
) {
}
