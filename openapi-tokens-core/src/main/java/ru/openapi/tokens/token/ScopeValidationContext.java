package ru.openapi.tokens.token;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Everything a {@link ru.openapi.tokens.token.spi.ScopeValidator} needs to decide whether the
 * requested token scopes may be granted: who asks, for which tenant, what is requested and what the
 * caller itself is allowed to do.
 *
 * @param ownerId           resolved owner of the token being created
 * @param tenantId          effective tenant (after the tenant-override policy is applied), may be {@code null}
 * @param requestedScopes   scopes asked for by the client, already trimmed and de-duplicated
 * @param callerAuthorities authorities currently held by the caller, empty when not resolvable
 * @param privileged        {@code true} when the caller may delegate any scope
 */
public record ScopeValidationContext(
        String ownerId,
        String tenantId,
        Set<String> requestedScopes,
        Set<String> callerAuthorities,
        boolean privileged
) {

    public ScopeValidationContext {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(requestedScopes, "requestedScopes");
        requestedScopes = Collections.unmodifiableSet(new LinkedHashSet<>(requestedScopes));
        callerAuthorities = callerAuthorities == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(callerAuthorities));
    }
}
