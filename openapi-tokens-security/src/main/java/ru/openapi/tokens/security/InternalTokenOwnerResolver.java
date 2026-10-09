package ru.openapi.tokens.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.TokenOwner;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;

/**
 * Resolves owner from the current Spring Security authentication (internal mode).
 */
public final class InternalTokenOwnerResolver implements TokenOwnerResolver {

    @Override
    public TokenOwner resolveCurrentOwner() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated principal for token owner resolution");
        }
        if (authentication instanceof ApiTokenAuthentication apiAuth && apiAuth.getApiToken() != null) {
            final ApiToken token = apiAuth.getApiToken();
            return new TokenOwner(token.ownerId(), token.tenantId());
        }
        final Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return TokenOwner.of(userDetails.getUsername());
        }
        if (principal instanceof String name && !name.isBlank()) {
            return TokenOwner.of(name);
        }
        return TokenOwner.of(authentication.getName());
    }
}
