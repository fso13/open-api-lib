package ru.openapi.tokens.token.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdentityScopeResolver")
class IdentityScopeResolverTest {

    private final IdentityScopeResolver resolver = new IdentityScopeResolver();

    @Test
    @DisplayName("Should return the same scopes unchanged")
    void shouldReturnSameScopes() {
        final Set<String> scopes = Set.of("a", "b");

        assertThat(resolver.resolve(scopes, "tenant-1")).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("Should return empty set for empty input")
    void shouldReturnEmptyForEmptyInput() {
        assertThat(resolver.resolve(Set.of(), null)).isEmpty();
    }
}
