package ru.openapi.tokens.persistence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_token_audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "owner_id", length = 128)
    private String ownerId;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "endpoint", length = 512)
    private String endpoint;

    @Column(name = "action", length = 128)
    private String action;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    Long getId() {
        return id;
    }

    UUID getTokenId() {
        return tokenId;
    }

    void setTokenId(UUID tokenId) {
        this.tokenId = tokenId;
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

    String getHttpMethod() {
        return httpMethod;
    }

    void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    String getEndpoint() {
        return endpoint;
    }

    void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    String getAction() {
        return action;
    }

    void setAction(String action) {
        this.action = action;
    }

    boolean isSuccess() {
        return success;
    }

    void setSuccess(boolean success) {
        this.success = success;
    }

    Integer getResponseStatus() {
        return responseStatus;
    }

    void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    String getIp() {
        return ip;
    }

    void setIp(String ip) {
        this.ip = ip;
    }

    String getUserAgent() {
        return userAgent;
    }

    void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
