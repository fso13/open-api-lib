package ru.openapi.tokens.token;

import java.util.Objects;
import java.util.UUID;

/**
 * Maps a token scope to one or more project authorities (1:M).
 */
public record ScopeMapping(
        UUID id,
        String tokenScope,
        String projectScope,
        String tenantId
) {

    public ScopeMapping {
        Objects.requireNonNull(tokenScope, "tokenScope");
        Objects.requireNonNull(projectScope, "projectScope");
    }
}
