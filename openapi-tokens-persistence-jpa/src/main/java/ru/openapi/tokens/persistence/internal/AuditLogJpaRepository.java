package ru.openapi.tokens.persistence.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByTokenIdOrderByCreatedAtDesc(UUID tokenId, Pageable pageable);

    @Query("""
            SELECT COUNT(a) FROM AuditLogEntity a
            WHERE a.tokenId = :tokenId
            """)
    long countByTokenId(@Param("tokenId") UUID tokenId);

    @Query("""
            SELECT COUNT(a) FROM AuditLogEntity a
            WHERE a.tokenId = :tokenId AND a.success = true
            """)
    long countSuccessfulByTokenId(@Param("tokenId") UUID tokenId);
}
