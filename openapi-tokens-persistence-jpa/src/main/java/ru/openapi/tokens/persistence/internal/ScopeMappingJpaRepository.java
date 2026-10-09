package ru.openapi.tokens.persistence.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScopeMappingJpaRepository extends JpaRepository<ScopeMappingEntity, UUID> {

    @Query("""
            SELECT m FROM ScopeMappingEntity m
            WHERE m.tokenScope IN :tokenScopes
              AND (m.tenantId IS NULL OR m.tenantId = :tenantId)
            """)
    List<ScopeMappingEntity> findByTokenScopes(
            @Param("tokenScopes") Collection<String> tokenScopes,
            @Param("tenantId") String tenantId
    );
}
