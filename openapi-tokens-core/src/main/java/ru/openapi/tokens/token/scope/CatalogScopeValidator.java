package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.exception.InvalidScopeException;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeValidator;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Requires every requested scope to be present in the {@link ScopeCatalog}.
 *
 * <p>The catalog is the single source of truth for "which scopes exist": configured descriptions,
 * the {@code scope_catalog} table and everything known from scope mappings.</p>
 */
public final class CatalogScopeValidator implements ScopeValidator {

    private final ScopeCatalog scopeCatalog;

    public CatalogScopeValidator(ScopeCatalog scopeCatalog) {
        this.scopeCatalog = Objects.requireNonNull(scopeCatalog, "scopeCatalog");
    }

    @Override
    public void validate(ScopeValidationContext context) {
        final Set<String> known = new TreeSet<>();
        for (ScopeInfo info : scopeCatalog.listScopes()) {
            known.add(info.scope());
        }
        final Set<String> unknown = new LinkedHashSet<>(context.requestedScopes());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new InvalidScopeException(InvalidScopeException.Reason.UNKNOWN_SCOPE, unknown);
        }
    }
}
