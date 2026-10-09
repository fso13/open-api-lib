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
        return hasAuthority(String.valueOf(permission));
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission
    ) {
        return hasAuthority(String.valueOf(permission));
    }

    @Override
    public boolean hasAuthority(String authority) {
        final Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
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
