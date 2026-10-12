package ru.openapi.tokens.token.service;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.ScopeValidationContext;
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
import ru.openapi.tokens.token.exception.TenantNotAllowedException;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.CallerAuthorityResolver;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default lifecycle service for API tokens (framework-agnostic).
 */
public class DefaultApiTokenService implements ApiTokenService {

    private static final String ACTION_CREATE = "CREATE_TOKEN";
    private static final String ACTION_REVOKE = "REVOKE_TOKEN";
    private static final String ACTION_BLOCK = "BLOCK_TOKEN";
    private static final String ACTION_UNBLOCK = "UNBLOCK_TOKEN";

    private final ApiTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final TokenOwnerResolver ownerResolver;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final ApiTokenServiceSettings settings;

    public DefaultApiTokenService(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            TokenOwnerResolver ownerResolver,
            Clock clock,
            int prefixLength,
            int maxTokensPerOwner
    ) {
        this(repository, tokenHasher, ownerResolver, null, clock,
                ApiTokenServiceSettings.defaults(prefixLength, maxTokensPerOwner));
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
        this(repository, tokenHasher, ownerResolver, auditLogRepository, clock,
                ApiTokenServiceSettings.defaults(prefixLength, maxTokensPerOwner));
    }

    public DefaultApiTokenService(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            TokenOwnerResolver ownerResolver,
            AuditLogRepository auditLogRepository,
            Clock clock,
            ApiTokenServiceSettings settings
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher");
        this.ownerResolver = Objects.requireNonNull(ownerResolver, "ownerResolver");
        this.auditLogRepository = auditLogRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public CreatedApiToken create(CreateTokenCommand command) {
        Objects.requireNonNull(command, "command");
        final TokenOwner owner = ownerResolver.resolveCurrentOwner();
        final String tenantId = resolveTenantId(command.tenantId(), owner);

        settings.scopeValidator().validate(new ScopeValidationContext(
                owner.ownerId(),
                tenantId,
                command.scopes(),
                callerAuthorities(),
                callerPrivileged()
        ));

        repository.lockOwnerQuota(owner.ownerId());
        final long existing = repository.countUsableByOwnerId(owner.ownerId());
        if (existing >= settings.maxTokensPerOwner()) {
            throw new QuotaExceededException(owner.ownerId(), settings.maxTokensPerOwner());
        }

        final RawToken raw = RawToken.generate(settings.prefixLength());
        final String hash = tokenHasher.hash(raw.secret());
        final Instant now = clock.instant();
        final TokenExpiry expiry = expiryFor(command, now);
        final RateLimitPolicy rateLimit = command.rateLimit() == null
                ? RateLimitPolicy.unlimited()
                : command.rateLimit();

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

        final ApiToken saved = repository.save(token);
        recordAudit(saved, ACTION_CREATE, now, 201);
        return new CreatedApiToken(saved, raw);
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
        final ApiToken revoked = repository.save(token.revoke(clock.instant()));
        recordAudit(revoked, ACTION_REVOKE, clock.instant(), 200);
        return revoked;
    }

    @Override
    public ApiToken block(BlockTokenCommand command) {
        final ApiToken token = requireOwnedToken(command.tokenId());
        return applyBlock(token, command);
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
    public List<ApiToken> listAll(int offset, int limit) {
        return repository.findPage(offset, limit);
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public ApiToken getAny(UUID tokenId) {
        return repository.findById(tokenId)
                .orElseThrow(() -> new ApiTokenNotFoundException(tokenId));
    }

    @Override
    public ApiToken forceRevoke(RevokeTokenCommand command) {
        final ApiToken token = getAny(command.tokenId());
        final ApiToken revoked = repository.save(token.revoke(clock.instant()));
        recordAudit(revoked, ACTION_REVOKE, clock.instant(), 200);
        return revoked;
    }

    @Override
    public ApiToken forceBlock(BlockTokenCommand command) {
        return applyBlock(getAny(command.tokenId()), command);
    }

    @Override
    public ApiToken touchLastUsed(UUID tokenId) {
        final ApiToken token = repository.findById(tokenId)
                .orElseThrow(() -> new ApiTokenNotFoundException(tokenId));
        if (!token.isUsable()) {
            throw new InvalidTokenStateException("Cannot touch non-active token", token.status());
        }
        final Instant now = clock.instant();
        if (!repository.touchLastUsed(tokenId, now)) {
            // Token stopped being usable between the read and the targeted update.
            throw new InvalidTokenStateException("Cannot touch non-active token", token.status());
        }
        return token.touchLastUsed(now);
    }

    private ApiToken applyBlock(ApiToken token, BlockTokenCommand command) {
        final ApiToken updated = command.block() ? token.block() : token.unblock();
        final ApiToken saved = repository.save(updated);
        recordAudit(saved, command.block() ? ACTION_BLOCK : ACTION_UNBLOCK, clock.instant(), 200);
        return saved;
    }

    private String resolveTenantId(String requestedTenantId, TokenOwner owner) {
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            return owner.tenantId();
        }
        final String requested = requestedTenantId.trim();
        if (settings.allowTenantOverride()) {
            return requested;
        }
        if (requested.equals(owner.tenantId())) {
            return requested;
        }
        throw new TenantNotAllowedException(requested, owner.tenantId());
    }

    private TokenExpiry expiryFor(CreateTokenCommand command, Instant now) {
        Instant expiresAt = command.expiresAt();
        if (expiresAt == null && command.slidingTtl() == null && settings.defaultTtl() != null) {
            expiresAt = now.plus(settings.defaultTtl());
        }
        return TokenExpiry.of(expiresAt, command.slidingTtl(), null).withCreatedAt(now);
    }

    private Set<String> callerAuthorities() {
        final CallerAuthorityResolver resolver = settings.callerAuthorityResolver();
        return resolver == null ? Set.of() : resolver.currentAuthorities();
    }

    private boolean callerPrivileged() {
        final CallerAuthorityResolver resolver = settings.callerAuthorityResolver();
        return resolver != null && resolver.isPrivileged();
    }

    private void recordAudit(ApiToken token, String action, Instant at, int status) {
        if (settings.auditRecorder() == null || token == null) {
            return;
        }
        settings.auditRecorder().record(new AuditEvent(
                token.id(),
                token.ownerId(),
                token.tenantId(),
                null,
                null,
                action,
                true,
                status,
                null,
                null,
                at
        ));
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
