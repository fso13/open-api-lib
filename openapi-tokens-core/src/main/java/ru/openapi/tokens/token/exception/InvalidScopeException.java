package ru.openapi.tokens.token.exception;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Raised when the requested token scopes cannot be granted.
 *
 * <p>The rejected scopes are exposed separately so that the REST layer can render them as a
 * machine readable problem property in a fail-closed way.</p>
 */
public class InvalidScopeException extends RuntimeException {

    /**
     * Why the scopes were rejected.
     */
    public enum Reason {
        /** Scope is not part of the configured/deployed scope catalog. */
        UNKNOWN_SCOPE,
        /** Scope matches a denied pattern (for example {@code ROLE_.*}). */
        DENIED_SCOPE,
        /** Caller tried to delegate an authority it does not hold itself. */
        NOT_GRANTABLE
    }

    private final Set<String> invalidScopes;
    private final Reason reason;

    public InvalidScopeException(Reason reason, Set<String> invalidScopes) {
        super("Invalid scopes (%s): %s".formatted(reason, invalidScopes));
        this.reason = reason == null ? Reason.UNKNOWN_SCOPE : reason;
        this.invalidScopes = invalidScopes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(invalidScopes));
    }

    public Set<String> getInvalidScopes() {
        return invalidScopes;
    }

    public Reason getReason() {
        return reason;
    }
}
