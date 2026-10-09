package ru.openapi.tokens.token.service;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenExpiry;
import ru.openapi.tokens.token.TokenOwner;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.exception.ApiTokenNotFoundException;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;
import ru.openapi.tokens.token.exception.QuotaExceededException;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default lifecycle service for API tokens (framework-agnostic).
 */
public class DefaultApiTokenService implements ApiTokenService {

    private final ApiTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final TokenOwnerResolver ownerResolver;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final int prefixLength;
    private final int maxTokensPerOwner;

    public DefaultApiTokenService(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            TokenOwnerResolver ownerResolver,
            Clock clock,
            int prefixLength,
            int maxTokensPerOwner
    ) {
        this(repository, tokenHasher, ownerResolver, null, clock, prefixLength, maxTokensPerOwner);
    }

    public DefaultApiTokenService(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            TokenOwnerResolver ownerResolver,
            AuditLogRepository auditLogRepository,
            Clock clock,
            int prefixLength,
            int maxTokensPerOwner
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher");
        this.ownerResolver = Objects.requireNonNull(ownerResolver, "ownerResolver");
        this.auditLogRepository = auditLogRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.prefixLength = prefixLength;
        this.maxTokensPerOwner = maxTokensPerOwner;
    }

    @Override
    public CreatedApiToken create(CreateTokenCommand command) {
        Objects.requireNonNull(command, "command");
        final TokenOwner owner = ownerResolver.resolveCurrentOwner();
        final long existing = repository.countByOwnerId(owner.ownerId());
        if (existing >= maxTokensPerOwner) {
            throw new QuotaExceededException(owner.ownerId(), maxTokensPerOwner);
        }

        final RawToken raw = RawToken.generate(prefixLength);
        final String hash = tokenHasher.hash(raw.secret());
        final Instant now = clock.instant();
        final TokenExpiry expiry = TokenExpiry.of(command.expiresAt(), command.slidingTtl(), null)
                .withCreatedAt(now);
        final RateLimitPolicy rateLimit = command.rateLimit() == null
                ? RateLimitPolicy.unlimited()
                : command.rateLimit();
        final String tenantId = command.tenantId() != null ? command.tenantId() : owner.tenantId();

        final ApiToken token = ApiToken.builder()
                .id(UUID.randomUUID())
                .name(command.name())
                .description(command.description())
                .credentials(new TokenCredentials(raw.prefix(), hash))
                .ownerId(owner.ownerId())
                .tenantId(tenantId)
                .scopes(command.scopes())
                .expiry(expiry)
                .rateLimit(rateLimit)
                .createdAt(now)
                .createdBy(owner.ownerId())
                .build();

        return new CreatedApiToken(repository.save(token), raw);
    }

    @Override
    public ApiToken get(UUID tokenId) {
        return requireOwnedToken(tokenId);
    }

    @Override
    public List<ApiToken> listOwn() {
        final TokenOwner owner = ownerResolver.resolveCurrentOwner();
        return repository.findByOwnerId(owner.ownerId());
    }

    @Override
    public ApiToken revoke(RevokeTokenCommand command) {
        final ApiToken token = requireOwnedToken(command.tokenId());
        return repository.save(token.revoke(clock.instant()));
    }

    @Override
    public ApiToken block(BlockTokenCommand command) {
        final ApiToken token = requireOwnedToken(command.tokenId());
        final ApiToken updated = command.block() ? token.block() : token.unblock();
        return repository.save(updated);
    }

    @Override
    public TokenUsageStats usageStats(UUID tokenId) {
        requireOwnedToken(tokenId);
        if (auditLogRepository == null) {
            return new TokenUsageStats(tokenId, 0, 0, 0, null);
        }
        return auditLogRepository.usageStats(tokenId);
    }

    @Override
    public List<ApiToken> listAll() {
        return repository.findAll();
    }

    @Override
    public ApiToken getAny(UUID tokenId) {
        return repository.findById(tokenId)
                .orElseThrow(() -> new ApiTokenNotFoundException(tokenId));
    }

    @Override
    public ApiToken forceRevoke(RevokeTokenCommand command) {
        final ApiToken token = getAny(command.tokenId());
        return repository.save(token.revoke(clock.instant()));
    }

    @Override
    public ApiToken forceBlock(BlockTokenCommand command) {
        final ApiToken token = getAny(command.tokenId());
        final ApiToken updated = command.block() ? token.block() : token.unblock();
        return repository.save(updated);
    }

    @Override
    public ApiToken touchLastUsed(UUID tokenId) {
        final ApiToken token = repository.findById(tokenId)
                .orElseThrow(() -> new ApiTokenNotFoundException(tokenId));
        if (!token.isUsable()) {
            throw new InvalidTokenStateException("Cannot touch non-active token", token.status());
        }
        return repository.save(token.touchLastUsed(clock.instant()));
    }

    private ApiToken requireOwnedToken(UUID tokenId) {
        final TokenOwner owner = ownerResolver.resolveCurrentOwner();
        final ApiToken token = repository.findById(tokenId)
                .orElseThrow(() -> new ApiTokenNotFoundException(tokenId));
        if (!owner.ownerId().equals(token.ownerId())) {
            throw new ApiTokenNotFoundException(tokenId);
        }
        return token;
    }
}
