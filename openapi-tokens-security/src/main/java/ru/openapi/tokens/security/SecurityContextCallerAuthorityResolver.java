package ru.openapi.tokens.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.openapi.tokens.token.spi.CallerAuthorityResolver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link CallerAuthorityResolver} backed by the current Spring Security context.
 *
 * <p>Used by scope validation to implement "never delegate more than you hold yourself": the
 * authorities of the authenticated caller (user roles, JWT authorities or authorities of another
 * API token) are compared with the authorities the new token would receive.</p>
 */
public final class SecurityContextCallerAuthorityResolver implements CallerAuthorityResolver {

    private final Set<String> privilegedAuthorities;

    public SecurityContextCallerAuthorityResolver() {
        this(Set.of("ROLE_ADMIN"));
    }

    public SecurityContextCallerAuthorityResolver(Set<String> privilegedAuthorities) {
        this.privilegedAuthorities = privilegedAuthorities == null
                ? Set.of()
                : Set.copyOf(privilegedAuthorities);
    }

    @Override
    public Set<String> currentAuthorities() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        final Set<String> authorities = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority != null && authority.getAuthority() != null) {
                authorities.add(authority.getAuthority());
            }
        }
        return authorities;
    }

    @Override
    public boolean isPrivileged() {
        if (privilegedAuthorities.isEmpty()) {
            return false;
        }
        return currentAuthorities().stream().anyMatch(privilegedAuthorities::contains);
    }

    public Set<String> privilegedAuthorities() {
        return privilegedAuthorities;
    }
}
