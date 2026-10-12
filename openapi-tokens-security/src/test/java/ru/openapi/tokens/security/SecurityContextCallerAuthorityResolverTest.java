package ru.openapi.tokens.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityContextCallerAuthorityResolver")
class SecurityContextCallerAuthorityResolverTest {

    private final SecurityContextCallerAuthorityResolver resolver =
            new SecurityContextCallerAuthorityResolver(Set.of("ROLE_ADMIN"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return the authorities of the current authentication")
    void shouldReturnAuthorities() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("payments:read")))
        );

        assertThat(resolver.currentAuthorities()).containsExactlyInAnyOrder("ROLE_USER", "payments:read");
        assertThat(resolver.isPrivileged()).isFalse();
    }

    @Test
    @DisplayName("Should mark admins as privileged")
    void shouldDetectPrivileged() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        assertThat(resolver.isPrivileged()).isTrue();
    }

    @Test
    @DisplayName("Should return no authorities without an authentication")
    void shouldReturnEmptyWithoutAuthentication() {
        assertThat(resolver.currentAuthorities()).isEmpty();
        assertThat(resolver.isPrivileged()).isFalse();
    }
}
