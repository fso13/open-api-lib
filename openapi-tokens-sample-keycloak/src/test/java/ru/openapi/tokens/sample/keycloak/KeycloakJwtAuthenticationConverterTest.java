package ru.openapi.tokens.sample.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Keycloak JWT -> authorities")
class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    @DisplayName("Should map realm and client roles to ROLE_* and keep scopes")
    void shouldMapRolesAndScopes() {
        final Jwt jwt = jwt(Map.of(
                "sub", "8f1d0c1e-0000-0000-0000-000000000001",
                "preferred_username", "demo",
                "scope", "openid profile",
                "realm_access", Map.of("roles", List.of("USER", "payments:read")),
                "resource_access", Map.of(
                        "openapi-tokens-api", Map.of("roles", List.of("ADMIN")),
                        "other-client", Map.of("roles", List.of("OTHER"))
                )
        ));

        final JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(authorities(authentication)).containsExactlyInAnyOrder(
                "SCOPE_openid", "SCOPE_profile",
                "ROLE_USER", "ROLE_payments:read", "ROLE_ADMIN", "ROLE_OTHER"
        );
        assertThat(authentication.getName()).isEqualTo("demo");
    }

    @Test
    @DisplayName("Should fall back to sub when preferred_username is missing")
    void shouldFallBackToSubject() {
        final Jwt jwt = jwt(Map.of("sub", "kc-user-1", "realm_access", Map.of("roles", List.of("USER"))));

        final JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("kc-user-1");
        assertThat(authorities(authentication)).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Should tolerate tokens without roles")
    void shouldTolerateMissingRoles() {
        final JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt(Map.of("sub", "kc-user-2")));

        assertThat(authorities(authentication)).isEmpty();
    }

    private static Collection<String> authorities(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("access-token", Instant.parse("2026-10-09T12:00:00Z"),
                Instant.parse("2026-10-09T12:05:00Z"), Map.of("alg", "none"), claims);
    }
}
