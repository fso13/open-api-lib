package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.spi.ScopeValidator;

/**
 * Accepts every requested scope. Used when {@code openapi.tokens.scopes.validation.mode=off}.
 */
public final class NoOpScopeValidator implements ScopeValidator {

    @Override
    public void validate(ScopeValidationContext context) {
        // intentionally empty
    }
}
