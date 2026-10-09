package ru.openapi.tokens.sample.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Keycloak roles -> UI authorities")
class KeycloakGrantedAuthoritiesMapperTest {

    private final KeycloakGrantedAuthoritiesMapper mapper = new KeycloakGrantedAuthoritiesMapper();

    @Test
    @DisplayName("Should add ROLE_* authorities from the ID token realm roles")
    void shouldAddRealmRoles() {
        final OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.parse("2026-10-09T12:00:00Z"),
                Instant.parse("2026-10-09T12:30:00Z"),
                Map.of(
                        "sub", "kc-user-1",
                        "realm_access", Map.of("roles", List.of("ADMIN", "USER"))
                )
        );
        final List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("SCOPE_openid"),
                new OidcUserAuthority(idToken, null)
        );

        final var mapped = mapper.mapAuthorities(authorities);

        assertThat(mapped).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_openid", "OIDC_USER", "ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("Should keep authorities untouched when there are no Keycloak roles")
    void shouldKeepOtherAuthorities() {
        final List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("SCOPE_openid"));

        assertThat(mapper.mapAuthorities(authorities)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_openid");
    }
}
