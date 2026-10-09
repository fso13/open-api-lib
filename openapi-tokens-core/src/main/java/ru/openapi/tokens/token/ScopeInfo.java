package ru.openapi.tokens.token;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A token scope together with its human readable description and the project authorities
 * it grants (1:M projection, empty in identity mode).
 */
public record ScopeInfo(String scope, String description, Set<String> projectScopes) {

    public ScopeInfo {
        Objects.requireNonNull(scope, "scope");
        scope = scope.trim();
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        description = description == null || description.isBlank() ? null : description.trim();
        projectScopes = projectScopes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(projectScopes));
    }

    public boolean hasDescription() {
        return description != null;
    }
}
