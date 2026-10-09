package ru.openapi.tokens.token.scope;

import ru.openapi.tokens.token.ScopeCatalogEntry;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeCatalogRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Default scope catalog: descriptions from configuration merged with descriptions stored in the
 * catalog repository (a stored description wins over the configured one), enriched with the
 * project authorities resolved through a {@link ScopeMappingSource} (identity mode → empty).
 *
 * <p>The catalog lists the union of:</p>
 * <ul>
 *     <li>scopes described in {@code openapi.tokens.scopes.catalog} / the catalog table;</li>
 *     <li>scopes that have at least one configured or stored mapping.</li>
 * </ul>
 */
public final class DefaultScopeCatalog implements ScopeCatalog {

    private final Map<String, String> configuredDescriptions;
    private final ScopeCatalogRepository catalogRepository;
    private final ScopeMappingSource mappingSource;

    /**
     * @param configuredDescriptions scope → description from configuration, never {@code null}
     * @param catalogRepository      stored descriptions, {@code null} when persistence is absent
     * @param mappingSource          source of token-scope → project-scope mappings
     */
    public DefaultScopeCatalog(
            Map<String, String> configuredDescriptions,
            ScopeCatalogRepository catalogRepository,
            ScopeMappingSource mappingSource
    ) {
        this.configuredDescriptions = configuredDescriptions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuredDescriptions));
        this.catalogRepository = catalogRepository;
        this.mappingSource = Objects.requireNonNull(mappingSource, "mappingSource");
    }

    @Override
    public List<ScopeInfo> listScopes() {
        final Map<String, String> descriptions = descriptions();
        final Set<String> scopes = new TreeSet<>(descriptions.keySet());
        scopes.addAll(mappingSource.knownTokenScopes());

        final List<ScopeInfo> result = new ArrayList<>(scopes.size());
        for (String scope : scopes) {
            result.add(new ScopeInfo(scope, descriptions.get(scope), mappingSource.projectScopesFor(scope, null)));
        }
        return Collections.unmodifiableList(result);
    }

    private Map<String, String> descriptions() {
        final Map<String, String> merged = new LinkedHashMap<>();
        configuredDescriptions.forEach((scope, description) -> {
            if (scope != null && !scope.isBlank()) {
                merged.put(scope.trim(), description);
            }
        });
        if (catalogRepository != null) {
            final List<ScopeCatalogEntry> stored = catalogRepository.findAll();
            if (stored != null) {
                for (ScopeCatalogEntry entry : stored) {
                    if (entry.description() != null) {
                        merged.put(entry.scope(), entry.description());
                    } else {
                        merged.putIfAbsent(entry.scope(), null);
                    }
                }
            }
        }
        return merged;
    }
}
