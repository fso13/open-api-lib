package ru.openapi.tokens.sample.keycloak;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adds Keycloak realm/client roles ({@code ROLE_ADMIN}, …) to the authorities of an interactive
 * OIDC login, so that the shared UI can gate {@code /admin/**} on {@code ROLE_ADMIN}.
 *
 * <p>Roles are read from the OIDC user attributes (ID token claims merged with the user-info
 * response), therefore the realm must expose {@code realm_access.roles} in the ID token or the
 * user-info response — see {@code openapi-tokens-sample-keycloak/docker/keycloak/realm-openapi-tokens.json}.</p>
 */
public class KeycloakGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities
    ) {
        final Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcAuthority) {
                KeycloakRoles.asRoleAuthorities(oidcAuthority.getAttributes())
                        .forEach(role -> mapped.add(new SimpleGrantedAuthority(role)));
            }
        }
        return mapped;
    }
}
