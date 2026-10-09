package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.spi.ScopeResolver;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 1:1 scope resolver — token scopes become project authorities as-is.
 */
public final class IdentityScopeResolver implements ScopeResolver {

    @Override
    public Set<String> resolve(Set<String> tokenScopes, String tenantId) {
        Objects.requireNonNull(tokenScopes, "tokenScopes");
        return Collections.unmodifiableSet(new LinkedHashSet<>(tokenScopes));
    }
}
