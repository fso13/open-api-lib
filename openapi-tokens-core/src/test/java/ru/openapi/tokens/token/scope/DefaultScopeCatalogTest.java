package ru.openapi.tokens.token.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.openapi.tokens.token.ScopeCatalogEntry;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.spi.ScopeCatalogRepository;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DefaultScopeCatalog")
class DefaultScopeCatalogTest {

    @Test
    @DisplayName("Should return configured scopes with descriptions, sorted by name")
    void listsConfiguredCatalog() {
        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(
                Map.of("payments:write", "Изменение платежей", "payments:read", "Чтение платежей"),
                null,
                new CompositeMappingSource(Map.of(), mock(ScopeMappingRepository.class))
        );

        final List<ScopeInfo> scopes = catalog.listScopes();

        assertThat(scopes).extracting(ScopeInfo::scope).containsExactly("payments:read", "payments:write");
        assertThat(scopes.getFirst().description()).isEqualTo("Чтение платежей");
        assertThat(scopes.getFirst().projectScopes()).isEmpty();
    }

    @Test
    @DisplayName("Should project scopes to authorities from mappings")
    void resolvesProjectScopes() {
        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(
                Map.of("payments", "Платежи"),
                null,
                new CompositeMappingSource(
                        Map.of("payments", Set.of("createPayment", "readPayment")),
                        mock(ScopeMappingRepository.class)
                )
        );

        final List<ScopeInfo> scopes = catalog.listScopes();

        assertThat(scopes).hasSize(1);
        assertThat(scopes.getFirst().scope()).isEqualTo("payments");
        assertThat(scopes.getFirst().projectScopes())
                .containsExactlyInAnyOrder("createPayment", "readPayment");
    }

    @Test
    @DisplayName("Should include scopes known only from mappings, without description")
    void includesMappingOnlyScopes() {
        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(
                Map.of("payments:read", "Чтение платежей"),
                null,
                new CompositeMappingSource(
                        Map.of("reports", Set.of("readReports")),
                        mock(ScopeMappingRepository.class)
                )
        );

        final List<ScopeInfo> scopes = catalog.listScopes();

        assertThat(scopes).extracting(ScopeInfo::scope).containsExactly("payments:read", "reports");
        final ScopeInfo reports = scopes.get(1);
        assertThat(reports.description()).isNull();
        assertThat(reports.hasDescription()).isFalse();
        assertThat(reports.projectScopes()).containsExactly("readReports");
    }

    @Test
    @DisplayName("Should prefer stored description over the configured one")
    void storedDescriptionWins() {
        final ScopeCatalogRepository repository = mock(ScopeCatalogRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                new ScopeCatalogEntry("payments:read", "Описание из БД"),
                new ScopeCatalogEntry("legacy", "Только в БД")
        ));

        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(
                Map.of("payments:read", "Описание из конфига", "demo:read", "Демо"),
                repository,
                new CompositeMappingSource(Map.of(), mock(ScopeMappingRepository.class))
        );

        final List<ScopeInfo> scopes = catalog.listScopes();

        assertThat(scopes).extracting(ScopeInfo::scope).containsExactly("demo:read", "legacy", "payments:read");
        assertThat(scopes).extracting(ScopeInfo::description)
                .containsExactly("Демо", "Только в БД", "Описание из БД");
    }

    @Test
    @DisplayName("Should keep the configured description when the stored row has none")
    void storedRowWithoutDescriptionKeepsConfiguredOne() {
        final ScopeCatalogRepository repository = mock(ScopeCatalogRepository.class);
        when(repository.findAll()).thenReturn(List.of(new ScopeCatalogEntry("payments:read", "  ")));

        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(
                Map.of("payments:read", "Из конфига"),
                repository,
                new CompositeMappingSource(Map.of(), mock(ScopeMappingRepository.class))
        );

        assertThat(catalog.listScopes()).singleElement()
                .satisfies(scope -> assertThat(scope.description()).isEqualTo("Из конфига"));
    }

    @Test
    @DisplayName("Should read stored scopes from the repository and ignore tenants in the projection")
    void storedEntriesAreListed() {
        final ScopeCatalogRepository repository = mock(ScopeCatalogRepository.class);
        when(repository.findAll()).thenReturn(List.of(new ScopeCatalogEntry("admin:all", "Полный доступ")));
        final ScopeMappingRepository mappings = mock(ScopeMappingRepository.class);
        when(mappings.findByTokenScopes(any(), any())).thenReturn(List.of(
                new ScopeMapping(UUID.randomUUID(), "admin:all", "ROLE_ADMIN", null)
        ));
        when(mappings.findAll()).thenReturn(List.of(
                new ScopeMapping(UUID.randomUUID(), "admin:all", "ROLE_ADMIN", null)
        ));

        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(Map.of(), repository,
                new CompositeMappingSource(Map.of(), mappings));

        assertThat(catalog.listScopes()).singleElement().satisfies(scope -> {
            assertThat(scope.scope()).isEqualTo("admin:all");
            assertThat(scope.description()).isEqualTo("Полный доступ");
            assertThat(scope.projectScopes()).containsExactly("ROLE_ADMIN");
        });
    }

    @Test
    @DisplayName("Should return an empty immutable list when nothing is configured")
    void emptyCatalog() {
        final DefaultScopeCatalog catalog = new DefaultScopeCatalog(
                Map.of(),
                null,
                new CompositeMappingSource(Map.of(), mock(ScopeMappingRepository.class))
        );

        assertThat(catalog.listScopes()).isEmpty();
    }
}
