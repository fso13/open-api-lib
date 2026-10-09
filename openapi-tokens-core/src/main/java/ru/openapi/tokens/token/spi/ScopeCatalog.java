package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ScopeInfo;

import java.util.List;

/**
 * Application service port exposing the scope catalog: every known token scope with its
 * description and the project authorities it grants.
 *
 * <p>Third-party apps may replace the default implementation by contributing their own
 * {@code ScopeCatalog} bean.</p>
 */
public interface ScopeCatalog {

    /**
     * All known scopes, sorted by scope name. Scopes without a description or without a mapping
     * are still returned so that the catalog can be rendered as a complete reference.
     */
    List<ScopeInfo> listScopes();
}
