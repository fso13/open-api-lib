package ru.openapi.tokens.token.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MappedScopeResolver")
class MappedScopeResolverTest {

    @Test
    @DisplayName("Should merge config mappings for multiple token scopes")
    void shouldMergeConfigMappings() {
        final ScopeMappingSource source = new CompositeMappingSource(
                Map.of(
                        "payment", Set.of("createPayment"),
                        "user", Set.of("readUser")
                ),
                emptyRepository()
        );
        final MappedScopeResolver resolver = new MappedScopeResolver(source);

        assertThat(resolver.resolve(Set.of("payment", "user"), "t1"))
                .containsExactlyInAnyOrder("createPayment", "readUser");
    }

    @Test
    @DisplayName("Should prefer DB mappings over config for same token scope")
    void shouldPreferDbOverConfig() {
        final ScopeMappingRepository repository = mock(ScopeMappingRepository.class);
        when(repository.findByTokenScopes(any(), eq("t1")))
                .thenReturn(List.of(new ScopeMapping(UUID.randomUUID(), "payment", "refundPayment", "t1")));

        final ScopeMappingSource source = new CompositeMappingSource(
                Map.of("payment", Set.of("createPayment")),
                repository
        );
        final MappedScopeResolver resolver = new MappedScopeResolver(source);

        assertThat(resolver.resolve(Set.of("payment"), "t1"))
                .containsExactly("refundPayment");
    }

    @Test
    @DisplayName("Should skip unknown token scopes")
    void shouldSkipUnknownScopes() {
        final MappedScopeResolver resolver = new MappedScopeResolver(
                new CompositeMappingSource(Map.of("payment", Set.of("createPayment")), emptyRepository())
        );

        assertThat(resolver.resolve(Set.of("unknown"), null)).isEmpty();
    }

    private static ScopeMappingRepository emptyRepository() {
        final ScopeMappingRepository repository = mock(ScopeMappingRepository.class);
        when(repository.findByTokenScopes(any(), any())).thenReturn(List.of());
        return repository;
    }
}
