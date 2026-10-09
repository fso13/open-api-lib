package ru.openapi.tokens.security;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.spi.ApiTokenAuthenticator;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.RateLimiter;
import ru.openapi.tokens.token.spi.ScopeResolver;
import ru.openapi.tokens.token.spi.TokenHasher;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates raw tokens: prefix lookup, hash verify, status/TTL, scopes, rate limit.
 */
public final class DefaultApiTokenAuthenticator implements ApiTokenAuthenticator {

    public static final String RATE_LIMIT_DENIED = "RATE_LIMIT_DENIED";

    private final ApiTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final ScopeResolver scopeResolver;
    private final RateLimiter rateLimiter;
    private final ApiTokenService apiTokenService;
    private final Clock clock;

    public DefaultApiTokenAuthenticator(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            ScopeResolver scopeResolver,
            RateLimiter rateLimiter,
            ApiTokenService apiTokenService,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.scopeResolver = Objects.requireNonNull(scopeResolver);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.apiTokenService = Objects.requireNonNull(apiTokenService);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<AuthenticatedToken> authenticate(RawToken rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        final Optional<ApiToken> found = repository.findByPrefix(rawToken.prefix());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final ApiToken token = found.get();
        if (!token.isUsable()) {
            return Optional.empty();
        }
        if (token.expiry().isExpired(clock)) {
            return Optional.empty();
        }
        if (!tokenHasher.matches(rawToken.secret(), token.credentials().tokenHash())) {
            return Optional.empty();
        }
        if (!rateLimiter.tryAcquire(token.id(), token.rateLimit())) {
            throw new RateLimitExceededException(token.id());
        }
        final Set<String> authorities = scopeResolver.resolve(token.scopes(), token.tenantId());
        apiTokenService.touchLastUsed(token.id());
        final ApiToken refreshed = repository.findById(token.id()).orElse(token);
        return Optional.of(new AuthenticatedToken(refreshed, authorities));
    }
}
