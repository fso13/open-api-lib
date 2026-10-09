package ru.openapi.tokens.token;

import ru.openapi.tokens.token.exception.EmptyScopesException;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * API token aggregate root (domain model, no persistence annotations).
 */
public final class ApiToken {

    private final UUID id;
    private final String name;
    private final String description;
    private final TokenCredentials credentials;
    private final String ownerId;
    private final String tenantId;
    private final TokenStatus status;
    private final Set<String> scopes;
    private final TokenExpiry expiry;
    private final RateLimitPolicy rateLimit;
    private final Instant createdAt;
    private final Instant revokedAt;
    private final String createdBy;

    private ApiToken(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.name = requireNonBlank(builder.name, "name");
        this.description = builder.description;
        this.credentials = Objects.requireNonNull(builder.credentials, "credentials");
        this.ownerId = requireNonBlank(builder.ownerId, "ownerId");
        this.tenantId = builder.tenantId;
        this.status = builder.status == null ? TokenStatus.ACTIVE : builder.status;
        this.scopes = normalizeScopes(builder.scopes);
        this.expiry = builder.expiry == null
                ? TokenExpiry.none().withCreatedAt(builder.createdAt)
                : builder.expiry.withCreatedAt(builder.createdAt);
        this.rateLimit = builder.rateLimit == null ? RateLimitPolicy.unlimited() : builder.rateLimit;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt");
        this.revokedAt = builder.revokedAt;
        this.createdBy = builder.createdBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ApiToken revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt");
        if (status == TokenStatus.REVOKED) {
            throw new InvalidTokenStateException("Token already revoked", status);
        }
        return toBuilder()
                .status(TokenStatus.REVOKED)
                .revokedAt(revokedAt)
                .build();
    }

    public ApiToken block() {
        if (status != TokenStatus.ACTIVE) {
            throw new InvalidTokenStateException("Only ACTIVE tokens can be blocked", status);
        }
        return toBuilder().status(TokenStatus.BLOCKED).build();
    }

    public ApiToken unblock() {
        if (status != TokenStatus.BLOCKED) {
            throw new InvalidTokenStateException("Only BLOCKED tokens can be unblocked", status);
        }
        return toBuilder().status(TokenStatus.ACTIVE).build();
    }

    public ApiToken markExpired() {
        return toBuilder().status(TokenStatus.EXPIRED).build();
    }

    public ApiToken touchLastUsed(Instant lastUsedAt) {
        Objects.requireNonNull(lastUsedAt, "lastUsedAt");
        return toBuilder().expiry(expiry.withLastUsedAt(lastUsedAt)).build();
    }

    public boolean isUsable() {
        return status == TokenStatus.ACTIVE;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(name)
                .description(description)
                .credentials(credentials)
                .ownerId(ownerId)
                .tenantId(tenantId)
                .status(status)
                .scopes(scopes)
                .expiry(expiry)
                .rateLimit(rateLimit)
                .createdAt(createdAt)
                .revokedAt(revokedAt)
                .createdBy(createdBy);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public TokenCredentials credentials() {
        return credentials;
    }

    public String ownerId() {
        return ownerId;
    }

    public String tenantId() {
        return tenantId;
    }

    public TokenStatus status() {
        return status;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public TokenExpiry expiry() {
        return expiry;
    }

    public RateLimitPolicy rateLimit() {
        return rateLimit;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    private static Set<String> normalizeScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new EmptyScopesException();
        }
        final Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                throw new EmptyScopesException();
            }
            normalized.add(scope.trim());
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static final class Builder {
        private UUID id;
        private String name;
        private String description;
        private TokenCredentials credentials;
        private String ownerId;
        private String tenantId;
        private TokenStatus status;
        private Set<String> scopes;
        private TokenExpiry expiry;
        private RateLimitPolicy rateLimit;
        private Instant createdAt;
        private Instant revokedAt;
        private String createdBy;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder credentials(TokenCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder ownerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder status(TokenStatus status) {
            this.status = status;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder expiry(TokenExpiry expiry) {
            this.expiry = expiry;
            return this;
        }

        public Builder rateLimit(RateLimitPolicy rateLimit) {
            this.rateLimit = rateLimit;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder revokedAt(Instant revokedAt) {
            this.revokedAt = revokedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public ApiToken build() {
            return new ApiToken(this);
        }
    }
}
