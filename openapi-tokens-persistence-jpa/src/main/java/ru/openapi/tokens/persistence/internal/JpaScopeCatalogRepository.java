package ru.openapi.tokens.persistence.internal;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.token.ScopeCatalogEntry;
import ru.openapi.tokens.token.spi.ScopeCatalogRepository;

import java.util.List;
import java.util.Objects;

@Repository
@Transactional(readOnly = true)
public class JpaScopeCatalogRepository implements ScopeCatalogRepository {

    private final ScopeCatalogJpaRepository jpaRepository;

    public JpaScopeCatalogRepository(ScopeCatalogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ScopeCatalogEntry> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public ScopeCatalogEntry save(ScopeCatalogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return toDomain(jpaRepository.save(toEntity(entry)));
    }

    @Override
    @Transactional
    public void deleteByScope(String scope) {
        jpaRepository.deleteById(scope);
    }

    private ScopeCatalogEntry toDomain(ScopeCatalogEntity entity) {
        return new ScopeCatalogEntry(entity.getScope(), entity.getDescription());
    }

    private ScopeCatalogEntity toEntity(ScopeCatalogEntry entry) {
        final ScopeCatalogEntity entity = new ScopeCatalogEntity();
        entity.setScope(entry.scope());
        entity.setDescription(entry.description());
        return entity;
    }
}
