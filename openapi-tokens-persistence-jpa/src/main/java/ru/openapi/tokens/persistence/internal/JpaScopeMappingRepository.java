package ru.openapi.tokens.persistence.internal;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.persistence.internal.ScopeMappingEntity;
import ru.openapi.tokens.persistence.internal.ScopeMappingJpaRepository;
import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaScopeMappingRepository implements ScopeMappingRepository {

    private final ScopeMappingJpaRepository jpaRepository;

    public JpaScopeMappingRepository(ScopeMappingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ScopeMapping> findByTokenScopes(Iterable<String> tokenScopes, String tenantId) {
        final List<String> scopes = new ArrayList<>();
        tokenScopes.forEach(scopes::add);
        if (scopes.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByTokenScopes(scopes, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ScopeMapping> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ScopeMapping save(ScopeMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        // The unique constraint is (token_scope, project_scope, tenant_id): reuse the existing row
        // for the natural key instead of failing on a duplicate insert.
        final ScopeMappingEntity entity = resolveTarget(mapping);
        entity.setTokenScope(mapping.tokenScope());
        entity.setProjectScope(mapping.projectScope());
        entity.setTenantId(mapping.tenantId());
        return toDomain(jpaRepository.save(entity));
    }

    private ScopeMappingEntity resolveTarget(ScopeMapping mapping) {
        if (mapping.id() != null) {
            final var byId = jpaRepository.findById(mapping.id());
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        final String tokenScope = mapping.tokenScope();
        final String projectScope = mapping.projectScope();
        final var existing = mapping.tenantId() == null
                ? jpaRepository.findByTokenScopeAndProjectScopeAndTenantIdIsNull(tokenScope, projectScope)
                : jpaRepository.findByTokenScopeAndProjectScopeAndTenantId(
                        tokenScope, projectScope, mapping.tenantId());
        return existing.orElseGet(() -> {
            final ScopeMappingEntity entity = new ScopeMappingEntity();
            entity.setId(mapping.id() == null ? UUID.randomUUID() : mapping.id());
            return entity;
        });
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private ScopeMapping toDomain(ScopeMappingEntity entity) {
        return new ScopeMapping(entity.getId(), entity.getTokenScope(), entity.getProjectScope(), entity.getTenantId());
    }
}
