package ru.openapi.tokens.sample.keycloak;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Turns a Keycloak access token into an authentication: the {@code scope} claim becomes plain
 * authorities ({@code payments:read}) and Keycloak realm/client roles become {@code ROLE_*} ones.
 *
 * <p>The principal name is {@code preferred_username}, falling back to {@code sub}.</p>
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        final Collection<GrantedAuthority> authorities = new LinkedHashSet<>(scopesConverter.convert(jwt));
        KeycloakRoles.asRoleAuthorities(jwt.getClaims())
                .forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
        return new JwtAuthenticationToken(jwt, authorities, principalName(jwt));
    }

    private static String principalName(Jwt jwt) {
        final String preferredUsername = jwt.getClaimAsString("preferred_username");
        return preferredUsername == null || preferredUsername.isBlank()
                ? jwt.getSubject()
                : preferredUsername;
    }
}
