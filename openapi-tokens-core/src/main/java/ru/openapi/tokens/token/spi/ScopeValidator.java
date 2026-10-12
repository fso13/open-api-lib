package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ScopeValidationContext;

/**
 * Decides whether the scopes requested for a new token may be granted.
 *
 * <p>Implementations must fail closed: throw {@link ru.openapi.tokens.token.exception.InvalidScopeException}
 * instead of silently granting more than allowed. Replace the default bean to plug in a product
 * specific policy.</p>
 */
@FunctionalInterface
public interface ScopeValidator {

    /**
     * Validates the requested scopes.
     *
     * @param context caller, tenant and requested scopes
     * @throws ru.openapi.tokens.token.exception.InvalidScopeException when at least one scope cannot be granted
     */
    void validate(ScopeValidationContext context);

    /**
     * Validator that accepts everything. Only suitable when scope validation is explicitly turned off.
     */
    static ScopeValidator allowAll() {
        return context -> {
        };
    }
}
