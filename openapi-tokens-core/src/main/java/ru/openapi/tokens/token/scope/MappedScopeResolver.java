package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.spi.ScopeResolver;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 1:M scope resolver using a {@link ScopeMappingSource}.
 */
public final class MappedScopeResolver implements ScopeResolver {

    private final ScopeMappingSource mappingSource;

    public MappedScopeResolver(ScopeMappingSource mappingSource) {
        this.mappingSource = Objects.requireNonNull(mappingSource, "mappingSource");
    }

    @Override
    public Set<String> resolve(Set<String> tokenScopes, String tenantId) {
        Objects.requireNonNull(tokenScopes, "tokenScopes");
        final Set<String> authorities = new LinkedHashSet<>();
        for (String tokenScope : tokenScopes) {
            authorities.addAll(mappingSource.projectScopesFor(tokenScope, tenantId));
        }
        return Collections.unmodifiableSet(authorities);
    }
}
