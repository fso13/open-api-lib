package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ScopeCatalogEntry;

import java.util.List;

/**
 * Persistence port for the scope catalog (scope → description).
 *
 * <p>Rows stored here override descriptions configured through
 * {@code openapi.tokens.scopes.catalog}.</p>
 */
public interface ScopeCatalogRepository {

    List<ScopeCatalogEntry> findAll();

    ScopeCatalogEntry save(ScopeCatalogEntry entry);

    void deleteByScope(String scope);
}
