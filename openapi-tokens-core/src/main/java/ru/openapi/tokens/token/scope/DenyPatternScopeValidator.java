package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.exception.InvalidScopeException;
import ru.openapi.tokens.token.spi.ScopeValidator;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects scopes matching any of the configured regular expressions.
 *
 * <p>This is the cheapest guard against privilege escalation through authority names: with the
 * default pattern {@code ROLE_.*} a user can never mint a token carrying a Spring Security role.</p>
 */
public final class DenyPatternScopeValidator implements ScopeValidator {

    private final List<Pattern> patterns;

    public DenyPatternScopeValidator(Collection<String> patterns) {
        this.patterns = (patterns == null ? List.<String>of() : patterns).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(Pattern::compile)
                .toList();
    }

    @Override
    public void validate(ScopeValidationContext context) {
        if (patterns.isEmpty()) {
            return;
        }
        final Set<String> denied = new LinkedHashSet<>();
        for (String scope : context.requestedScopes()) {
            if (scope == null) {
                continue;
            }
            for (Pattern pattern : patterns) {
                if (pattern.matcher(scope).matches()) {
                    denied.add(scope);
                    break;
                }
            }
        }
        if (!denied.isEmpty()) {
            throw new InvalidScopeException(InvalidScopeException.Reason.DENIED_SCOPE, denied);
        }
    }
}
