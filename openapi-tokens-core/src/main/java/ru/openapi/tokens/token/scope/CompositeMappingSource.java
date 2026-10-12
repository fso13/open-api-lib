package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        return projectScopesFor(Set.of(tokenScope), tenantId).getOrDefault(tokenScope, Set.of());
    }

    @Override
    public Map<String, Set<String>> projectScopesFor(Set<String> tokenScopes, String tenantId) {
        Objects.requireNonNull(tokenScopes, "tokenScopes");
        if (tokenScopes.isEmpty()) {
            return Map.of();
        }
        final List<ScopeMapping> dbRows = repository.findByTokenScopes(tokenScopes, tenantId);
        final Map<String, Set<String>> fromDb = new LinkedHashMap<>();
        if (dbRows != null) {
            for (ScopeMapping mapping : dbRows) {
                if (tokenScopes.contains(mapping.tokenScope())) {
                    fromDb.computeIfAbsent(mapping.tokenScope(), key -> new LinkedHashSet<>())
                            .add(mapping.projectScope());
                }
            }
        }
        final Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String tokenScope : tokenScopes) {
            final Set<String> stored = fromDb.get(tokenScope);
            // A single stored row for a scope fully overrides the configured mapping for that scope.
            result.put(tokenScope, stored != null && !stored.isEmpty()
                    ? Collections.unmodifiableSet(stored)
                    : configMappings.getOrDefault(tokenScope, Set.of()));
        }
        return Collections.unmodifiableMap(result);
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
