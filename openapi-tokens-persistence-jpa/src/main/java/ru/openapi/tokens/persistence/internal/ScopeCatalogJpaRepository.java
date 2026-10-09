package ru.openapi.tokens.persistence.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScopeCatalogJpaRepository extends JpaRepository<ScopeCatalogEntity, String> {
}
