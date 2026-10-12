package ru.openapi.tokens.web;

import org.springframework.stereotype.Component;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.web.dto.ApiTokenResponse;
import ru.openapi.tokens.web.dto.AuditEventResponse;
import ru.openapi.tokens.web.dto.CreateTokenRequest;
import ru.openapi.tokens.web.dto.CreatedTokenResponse;
import ru.openapi.tokens.web.dto.ScopeInfoResponse;
import ru.openapi.tokens.web.dto.TokenUsageStatsResponse;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Component
public class ApiTokenWebMapper {

    private final Clock clock;

    public ApiTokenWebMapper() {
        this(Clock.systemUTC());
    }

    public ApiTokenWebMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CreateTokenCommand toCommand(CreateTokenRequest request) {
        return new CreateTokenCommand(
                request.name(),
                request.description(),
                request.scopes(),
                request.expiresAt(),
                request.slidingTtlSeconds() == null ? null : Duration.ofSeconds(request.slidingTtlSeconds()),
                new RateLimitPolicy(request.rateLimitRequests(), request.rateLimitWindowSeconds()),
                request.tenantId()
        );
    }

    public ApiTokenResponse toResponse(ApiToken token) {
        return new ApiTokenResponse(
                token.id(),
                token.name(),
                token.description(),
                token.credentials().prefix(),
                token.ownerId(),
                token.tenantId(),
                token.status().name(),
                token.effectiveStatus(clock.instant()).name(),
                token.scopes(),
                token.expiry().expiresAt(),
                token.expiry().lastUsedAt(),
                token.expiry().slidingTtlSeconds(),
                token.rateLimit().requests(),
                token.rateLimit().windowSeconds(),
                token.createdBy(),
                token.createdAt(),
                token.revokedAt()
        );
    }

    public CreatedTokenResponse toCreatedResponse(CreatedApiToken created) {
        return new CreatedTokenResponse(toResponse(created.token()), created.rawToken().value());
    }

    public TokenUsageStatsResponse toStatsResponse(TokenUsageStats stats) {
        return new TokenUsageStatsResponse(
                stats.tokenId(),
                stats.totalRequests(),
                stats.successfulRequests(),
                stats.failedRequests(),
                stats.lastUsedAt()
        );
    }

    public List<ScopeInfoResponse> toScopeResponses(List<ScopeInfo> scopes) {
        return scopes.stream().map(this::toScopeResponse).toList();
    }

    public ScopeInfoResponse toScopeResponse(ScopeInfo scope) {
        return new ScopeInfoResponse(scope.scope(), scope.description(), scope.projectScopes());
    }

    public List<AuditEventResponse> toAuditResponses(List<AuditEvent> events) {
        return events.stream().map(this::toAuditResponse).toList();
    }

    public AuditEventResponse toAuditResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.tokenId(),
                event.ownerId(),
                event.tenantId(),
                event.httpMethod(),
                event.endpoint(),
                event.action(),
                event.success(),
                event.responseStatus(),
                event.ip(),
                event.userAgent(),
                event.createdAt()
        );
    }
}
