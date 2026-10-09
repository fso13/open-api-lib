package ru.openapi.tokens.token.scope;

import java.util.Set;

/**
 * Source of token-scope → project-scope mappings (config and/or DB).
 */
public interface ScopeMappingSource {

    Set<String> projectScopesFor(String tokenScope, String tenantId);

    /**
     * Every token scope known to this source, regardless of tenant.
     */
    Set<String> knownTokenScopes();
}
