package ru.openapi.tokens.token;

import java.util.Objects;

/**
 * Catalog row: a declared token scope and its optional human readable description.
 * Descriptions are metadata only — they never take part in authorization decisions.
 */
public record ScopeCatalogEntry(String scope, String description) {

    public ScopeCatalogEntry {
        Objects.requireNonNull(scope, "scope");
        scope = scope.trim();
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
