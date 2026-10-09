package ru.openapi.tokens.persistence.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import ru.openapi.tokens.token.TokenStatus;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_token")
public class ApiTokenEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "prefix", nullable = false, length = 16, unique = true)
    private String prefix;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TokenStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "sliding_ttl_seconds")
    private Integer slidingTtlSeconds;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rate_limit_requests")
    private Integer rateLimitRequests;

    @Column(name = "rate_limit_window_seconds")
    private Integer rateLimitWindowSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "api_token_scope", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "scope", nullable = false, length = 128)
    private Set<String> scopes = new LinkedHashSet<>();

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getPrefix() {
        return prefix;
    }

    void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    String getTokenHash() {
        return tokenHash;
    }

    void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    String getOwnerId() {
        return ownerId;
    }

    void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    String getTenantId() {
        return tenantId;
    }

    void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    TokenStatus getStatus() {
        return status;
    }

    void setStatus(TokenStatus status) {
        this.status = status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    Integer getSlidingTtlSeconds() {
        return slidingTtlSeconds;
    }

    void setSlidingTtlSeconds(Integer slidingTtlSeconds) {
        this.slidingTtlSeconds = slidingTtlSeconds;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    Integer getRateLimitRequests() {
        return rateLimitRequests;
    }

    void setRateLimitRequests(Integer rateLimitRequests) {
        this.rateLimitRequests = rateLimitRequests;
    }

    Integer getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    void setRateLimitWindowSeconds(Integer rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    String getCreatedBy() {
        return createdBy;
    }

    void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    Set<String> getScopes() {
        return scopes;
    }

    void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }
}
