package ru.openapi.tokens.sample.keycloak;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts Keycloak roles from JWT / ID token / user-info claims.
 *
 * <p>Realm roles live in {@code realm_access.roles}, client roles in
 * {@code resource_access.<client-id>.roles}.</p>
 */
public final class KeycloakRoles {

    public static final String REALM_ACCESS = "realm_access";
    public static final String RESOURCE_ACCESS = "resource_access";
    public static final String ROLES = "roles";

    /** Spring Security role prefix. */
    public static final String ROLE_PREFIX = "ROLE_";

    private KeycloakRoles() {
    }

    /**
     * All realm and client roles of the given claims.
     */
    public static Set<String> fromClaims(Map<String, Object> claims) {
        final Set<String> roles = new LinkedHashSet<>();
        if (claims == null) {
            return roles;
        }
        roles.addAll(rolesOf(claims.get(REALM_ACCESS)));
        final Object resourceAccess = claims.get(RESOURCE_ACCESS);
        if (resourceAccess instanceof Map<?, ?> clients) {
            clients.values().forEach(client -> roles.addAll(rolesOf(client)));
        }
        return roles;
    }

    /**
     * The given roles as Spring Security role authorities ({@code ADMIN} → {@code ROLE_ADMIN}).
     */
    public static Set<String> asRoleAuthorities(Map<String, Object> claims) {
        final Set<String> authorities = new LinkedHashSet<>();
        for (String role : fromClaims(claims)) {
            authorities.add(role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role);
        }
        return authorities;
    }

    private static Collection<String> rolesOf(Object container) {
        if (!(container instanceof Map<?, ?> map)) {
            return List.of();
        }
        final Object roles = map.get(ROLES);
        if (!(roles instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
