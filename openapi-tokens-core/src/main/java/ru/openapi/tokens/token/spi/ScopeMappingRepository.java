package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ScopeMapping;

import java.util.List;

/**
 * Persistence port for token-scope → project-scope mappings.
 */
public interface ScopeMappingRepository {

    List<ScopeMapping> findByTokenScopes(Iterable<String> tokenScopes, String tenantId);

    /**
     * All stored mappings, regardless of tenant (used to enumerate known token scopes).
     */
    List<ScopeMapping> findAll();

    ScopeMapping save(ScopeMapping mapping);

    void deleteById(java.util.UUID id);
}
