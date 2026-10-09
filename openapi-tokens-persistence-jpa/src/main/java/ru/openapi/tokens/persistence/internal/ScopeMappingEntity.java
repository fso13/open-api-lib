package ru.openapi.tokens.persistence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "scope_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_scope_mapping",
                columnNames = {"token_scope", "project_scope", "tenant_id"}
        )
)
public class ScopeMappingEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "token_scope", nullable = false, length = 128)
    private String tokenScope;

    @Column(name = "project_scope", nullable = false, length = 128)
    private String projectScope;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getTokenScope() {
        return tokenScope;
    }

    void setTokenScope(String tokenScope) {
        this.tokenScope = tokenScope;
    }

    String getProjectScope() {
        return projectScope;
    }

    void setProjectScope(String projectScope) {
        this.projectScope = projectScope;
    }

    String getTenantId() {
        return tenantId;
    }

    void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
