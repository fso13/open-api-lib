package ru.openapi.tokens.persistence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scope_catalog")
public class ScopeCatalogEntity {

    @Id
    @Column(name = "scope", nullable = false, length = 128)
    private String scope;

    @Column(name = "description", length = 512)
    private String description;

    String getScope() {
        return scope;
    }

    void setScope(String scope) {
        this.scope = scope;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }
}
