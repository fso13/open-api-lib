package ru.openapi.tokens.persistence.internal;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenJpaRepository extends JpaRepository<ApiTokenEntity, UUID> {

    @EntityGraph(attributePaths = "scopes")
    Optional<ApiTokenEntity> findByPrefix(String prefix);

    @EntityGraph(attributePaths = "scopes")
    List<ApiTokenEntity> findByOwnerId(String ownerId);

    long countByOwnerId(String ownerId);
}
