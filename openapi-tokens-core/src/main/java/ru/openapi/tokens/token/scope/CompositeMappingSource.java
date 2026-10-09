package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hybrid mapping source: config bindings + DB rows. DB wins for a token scope when present.
 */
public final class CompositeMappingSource implements ScopeMappingSource {

    private final Map<String, Set<String>> configMappings;
    private final ScopeMappingRepository repository;

    public CompositeMappingSource(Map<String, Set<String>> configMappings, ScopeMappingRepository repository) {
        this.configMappings = configMappings == null
                ? Map.of()
                : Collections.unmodifiableMap(configMappings.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> Set.copyOf(e.getValue())
                )));
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Set<String> projectScopesFor(String tokenScope, String tenantId) {
        Objects.requireNonNull(tokenScope, "tokenScope");
        final List<ScopeMapping> dbRows = repository.findByTokenScopes(List.of(tokenScope), tenantId);
        if (dbRows != null && !dbRows.isEmpty()) {
            final Set<String> fromDb = new LinkedHashSet<>();
            for (ScopeMapping mapping : dbRows) {
                if (tokenScope.equals(mapping.tokenScope())) {
                    fromDb.add(mapping.projectScope());
                }
            }
            if (!fromDb.isEmpty()) {
                return Collections.unmodifiableSet(fromDb);
            }
        }
        return configMappings.getOrDefault(tokenScope, Set.of());
    }

    @Override
    public Set<String> knownTokenScopes() {
        final Set<String> scopes = new LinkedHashSet<>(configMappings.keySet());
        final List<ScopeMapping> stored = repository.findAll();
        if (stored != null) {
            for (ScopeMapping mapping : stored) {
                scopes.add(mapping.tokenScope());
            }
        }
        return Collections.unmodifiableSet(scopes);
    }
}
