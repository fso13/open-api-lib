package ru.openapi.tokens.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import ru.openapi.tokens.token.spi.ApiTokenPermissionChecker;

import java.io.Serializable;
import java.util.Arrays;

/**
 * {@link PermissionEvaluator} + {@link ApiTokenPermissionChecker} for programmatic checks.
 */
public final class ApiTokenPermissionEvaluator implements PermissionEvaluator, ApiTokenPermissionChecker {

    private final AuthenticationSupplier authenticationSupplier;

    public ApiTokenPermissionEvaluator(AuthenticationSupplier authenticationSupplier) {
        this.authenticationSupplier = authenticationSupplier;
    }

    public ApiTokenPermissionEvaluator() {
        this(() -> org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return hasAuthority(authentication, String.valueOf(permission));
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission
    ) {
        return hasAuthority(authentication, String.valueOf(permission));
    }

    @Override
    public boolean hasAuthority(String authority) {
        return hasAuthority(authenticationSupplier.get(), authority);
    }

    /**
     * Checks the given authentication when present, falling back to the current security context
     * otherwise. Using the passed principal avoids deciding on the wrong identity when the
     * expression is evaluated for another authentication.
     */
    private boolean hasAuthority(Authentication authentication, String authority) {
        final Authentication effective = authentication != null ? authentication : authenticationSupplier.get();
        if (effective == null || !effective.isAuthenticated()) {
            return false;
        }
        return effective.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    @Override
    public boolean hasAnyAuthority(String... authorities) {
        return Arrays.stream(authorities).anyMatch(this::hasAuthority);
    }

    @FunctionalInterface
    public interface AuthenticationSupplier {
        Authentication get();
    }
}
