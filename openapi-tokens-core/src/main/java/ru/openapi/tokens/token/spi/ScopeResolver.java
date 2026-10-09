package ru.openapi.tokens.token.spi;

import java.util.Set;

/**
 * Resolves token scopes into project authorities.
 */
public interface ScopeResolver {

    Set<String> resolve(Set<String> tokenScopes, String tenantId);
}
