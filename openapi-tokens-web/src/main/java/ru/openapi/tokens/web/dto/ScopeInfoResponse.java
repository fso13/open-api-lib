package ru.openapi.tokens.web.dto;

import java.util.Set;

/**
 * One entry of the scope catalog: the token scope, its description (may be {@code null}) and the
 * project authorities it grants.
 */
public record ScopeInfoResponse(
        String scope,
        String description,
        Set<String> projectScopes
) {
}
