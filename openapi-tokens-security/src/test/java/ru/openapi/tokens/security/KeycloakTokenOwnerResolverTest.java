package ru.openapi.tokens.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KeycloakTokenOwnerResolver")
class KeycloakTokenOwnerResolverTest {

    @Test
    @DisplayName("Should resolve owner from JWT sub claim")
    void shouldResolveFromSub() {
        final Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-10-07T12:00:00Z"),
                Instant.parse("2026-10-07T13:00:00Z"),
                Map.of("alg", "none"),
                Map.of("sub", "keycloak-user-1", "tenant_id", "tenant-a")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        try {
            final var owner = new KeycloakTokenOwnerResolver().resolveCurrentOwner();
            assertThat(owner.ownerId()).isEqualTo("keycloak-user-1");
            assertThat(owner.tenantId()).isEqualTo("tenant-a");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Should resolve owner from an OIDC login principal (oauth2Login)")
    void shouldResolveFromOidcUser() {
        final OidcUser oidcUser = oidcUser(Map.of("sub", "keycloak-user-2", "tenant_id", "tenant-b"));
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak"));
        try {
            final var owner = new KeycloakTokenOwnerResolver().resolveCurrentOwner();
            assertThat(owner.ownerId()).isEqualTo("keycloak-user-2");
            assertThat(owner.tenantId()).isEqualTo("tenant-b");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Should read the tenant from a custom claim")
    void shouldUseConfiguredTenantClaim() {
        final OidcUser oidcUser = oidcUser(Map.of("sub", "keycloak-user-3", "org_id", "org-42"));
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak"));
        try {
            final var owner = new KeycloakTokenOwnerResolver("org_id").resolveCurrentOwner();
            assertThat(owner.ownerId()).isEqualTo("keycloak-user-3");
            assertThat(owner.tenantId()).isEqualTo("org-42");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Should fall back to the default tenant claim when blank")
    void shouldFallBackToDefaultTenantClaim() {
        assertThat(new KeycloakTokenOwnerResolver("  ").tenantClaim()).isEqualTo("tenant_id");
        assertThat(new KeycloakTokenOwnerResolver().tenantClaim()).isEqualTo("tenant_id");
    }

    @Test
    @DisplayName("Should fail on an unsupported authentication")
    void shouldFailOnUnsupportedAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone", "n/a"));
        try {
            assertThatThrownBy(() -> new KeycloakTokenOwnerResolver().resolveCurrentOwner())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Keycloak owner resolution");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Should fail when the subject claim is missing")
    void shouldFailWithoutSubject() {
        final Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-10-07T12:00:00Z"),
                Instant.parse("2026-10-07T13:00:00Z"),
                Map.of("alg", "none"),
                Map.of("tenant_id", "tenant-a")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        try {
            assertThatThrownBy(() -> new KeycloakTokenOwnerResolver().resolveCurrentOwner())
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static OidcUser oidcUser(Map<String, Object> claims) {
        final OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.parse("2026-10-07T12:00:00Z"),
                Instant.parse("2026-10-07T13:00:00Z"),
                claims
        );
        return new DefaultOidcUser(List.of(), idToken);
    }
}
