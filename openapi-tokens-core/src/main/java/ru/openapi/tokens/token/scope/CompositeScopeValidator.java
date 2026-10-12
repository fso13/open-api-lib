package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.spi.ScopeValidator;

import java.util.List;
import java.util.Objects;

/**
 * Applies several validators in order; the first rejection wins.
 */
public final class CompositeScopeValidator implements ScopeValidator {

    private final List<ScopeValidator> delegates;

    public CompositeScopeValidator(List<ScopeValidator> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
    }

    public static ScopeValidator of(ScopeValidator... validators) {
        return new CompositeScopeValidator(List.of(validators));
    }

    @Override
    public void validate(ScopeValidationContext context) {
        for (ScopeValidator delegate : delegates) {
            delegate.validate(context);
        }
    }

    public List<ScopeValidator> delegates() {
        return delegates;
    }
}
