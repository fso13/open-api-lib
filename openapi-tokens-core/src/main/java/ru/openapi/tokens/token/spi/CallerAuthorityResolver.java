package ru.openapi.tokens.token.spi;

import java.util.Set;

/**
 * Resolves the authorities of the caller that is creating a token.
 *
 * <p>The core module has no dependency on Spring Security, so the security adapter
 * ({@code SecurityContextCallerAuthorityResolver}) provides the implementation. When no resolver is
 * registered, callers are treated as having no authorities and as not privileged.</p>
 */
public interface CallerAuthorityResolver {

    /**
     * Authorities of the current caller (for example Spring Security authorities of the JWT/user).
     */
    Set<String> currentAuthorities();

    /**
     * @return {@code true} when the caller may delegate any scope
     */
    default boolean isPrivileged() {
        return false;
    }

    /**
     * Resolver for contexts without any caller information.
     */
    static CallerAuthorityResolver none() {
        return new CallerAuthorityResolver() {
            @Override
            public Set<String> currentAuthorities() {
                return Set.of();
            }
        };
    }
}
