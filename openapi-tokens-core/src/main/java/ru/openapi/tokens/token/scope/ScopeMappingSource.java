package ru.openapi.tokens.token.scope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Source of token-scope → project-scope mappings (config and/or DB).
 */
public interface ScopeMappingSource {

    Set<String> projectScopesFor(String tokenScope, String tenantId);

    /**
     * Batched variant of {@link #projectScopesFor(String, String)}: implementations should resolve
     * all requested scopes with a single query. The default implementation keeps the per-scope
     * behaviour for backward compatibility.
     */
    default Map<String, Set<String>> projectScopesFor(Set<String> tokenScopes, String tenantId) {
        final Map<String, Set<String>> result = new LinkedHashMap<>();
        if (tokenScopes == null) {
            return result;
        }
        for (String tokenScope : tokenScopes) {
            result.put(tokenScope, projectScopesFor(tokenScope, tenantId));
        }
        return result;
    }

    /**
     * Every token scope known to this source, regardless of tenant.
     */
    Set<String> knownTokenScopes();
}
