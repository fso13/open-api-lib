package ru.openapi.tokens.token.spi;

/**
 * Programmatic permission checks against resolved token authorities.
 */
public interface ApiTokenPermissionChecker {

    boolean hasAuthority(String authority);

    boolean hasAnyAuthority(String... authorities);
}
