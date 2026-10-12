package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.exception.InvalidScopeException;
import ru.openapi.tokens.token.spi.ScopeResolver;
import ru.openapi.tokens.token.spi.ScopeValidator;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Prevents delegation of authorities the caller does not hold itself.
 *
 * <p>The requested scopes are projected to project authorities through the configured
 * {@link ScopeResolver} (identity → the scopes themselves, mapped → the mapped authorities) and
 * every resulting authority must be present in {@link ScopeValidationContext#callerAuthorities()}.
 * Privileged callers (for example admins) bypass the check.</p>
 */
public final class CallerAuthorityScopeValidator implements ScopeValidator {

    private final ScopeResolver scopeResolver;

    public CallerAuthorityScopeValidator(ScopeResolver scopeResolver) {
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver");
    }

    @Override
    public void validate(ScopeValidationContext context) {
        if (context.privileged()) {
            return;
        }
        final Set<String> projected = scopeResolver.resolve(context.requestedScopes(), context.tenantId());
        final Set<String> notGrantable = new LinkedHashSet<>(projected);
        notGrantable.removeAll(context.callerAuthorities());
        if (!notGrantable.isEmpty()) {
            throw new InvalidScopeException(InvalidScopeException.Reason.NOT_GRANTABLE, notGrantable);
        }
    }
}
