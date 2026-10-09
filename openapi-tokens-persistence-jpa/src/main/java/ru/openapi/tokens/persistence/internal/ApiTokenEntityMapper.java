package ru.openapi.tokens.persistence.internal;

import org.springframework.stereotype.Component;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenExpiry;

import java.time.Duration;
import java.util.LinkedHashSet;

@Component
public class ApiTokenEntityMapper {

    public ApiTokenEntity toEntity(ApiToken domain) {
        final ApiTokenEntity entity = new ApiTokenEntity();
        entity.setId(domain.id());
        entity.setPrefix(domain.credentials().prefix());
        entity.setTokenHash(domain.credentials().tokenHash());
        entity.setName(domain.name());
        entity.setDescription(domain.description());
        entity.setOwnerId(domain.ownerId());
        entity.setTenantId(domain.tenantId());
        entity.setStatus(domain.status());
        entity.setExpiresAt(domain.expiry().expiresAt());
        entity.setSlidingTtlSeconds(domain.expiry().slidingTtlSeconds());
        entity.setLastUsedAt(domain.expiry().lastUsedAt());
        entity.setRateLimitRequests(domain.rateLimit().requests());
        entity.setRateLimitWindowSeconds(domain.rateLimit().windowSeconds());
        entity.setCreatedAt(domain.createdAt());
        entity.setRevokedAt(domain.revokedAt());
        entity.setCreatedBy(domain.createdBy());
        entity.setScopes(new LinkedHashSet<>(domain.scopes()));
        return entity;
    }

    public ApiToken toDomain(ApiTokenEntity entity) {
        final Duration sliding = entity.getSlidingTtlSeconds() == null
                ? null
                : Duration.ofSeconds(entity.getSlidingTtlSeconds());
        final TokenExpiry expiry = TokenExpiry.of(entity.getExpiresAt(), sliding, entity.getLastUsedAt())
                .withCreatedAt(entity.getCreatedAt());
        return ApiToken.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .credentials(new TokenCredentials(entity.getPrefix(), entity.getTokenHash()))
                .ownerId(entity.getOwnerId())
                .tenantId(entity.getTenantId())
                .status(entity.getStatus())
                .scopes(new LinkedHashSet<>(entity.getScopes()))
                .expiry(expiry)
                .rateLimit(new RateLimitPolicy(entity.getRateLimitRequests(), entity.getRateLimitWindowSeconds()))
                .createdAt(entity.getCreatedAt())
                .revokedAt(entity.getRevokedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
