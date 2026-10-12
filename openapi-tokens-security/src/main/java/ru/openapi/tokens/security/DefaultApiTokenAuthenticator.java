package ru.openapi.tokens.security;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.exception.ApiTokenNotFoundException;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;
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
 * Authenticates raw tokens: prefix lookup, status/TTL, hash verify, rate limit, scopes.
 *
 * <p>Failed attempts are throttled <em>before</em> the expensive hash verification when a
 * {@code failedAttemptPolicy} is configured, so guessing prefixes/secrets cannot drive unbounded
 * Argon2 work.</p>
 */
public final class DefaultApiTokenAuthenticator implements ApiTokenAuthenticator {

    public static final String RATE_LIMIT_DENIED = "RATE_LIMIT_DENIED";

    private static final String FAILED_ATTEMPT_KEY_PREFIX = "auth-fail:";

    private final ApiTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final ScopeResolver scopeResolver;
    private final RateLimiter rateLimiter;
    private final ApiTokenService apiTokenService;
    private final Clock clock;
    private final RateLimitPolicy failedAttemptPolicy;
    private final boolean rejectEmptyAuthorities;

    public DefaultApiTokenAuthenticator(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            ScopeResolver scopeResolver,
            RateLimiter rateLimiter,
            ApiTokenService apiTokenService,
            Clock clock
    ) {
        this(repository, tokenHasher, scopeResolver, rateLimiter, apiTokenService, clock, null, false);
    }

    public DefaultApiTokenAuthenticator(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            ScopeResolver scopeResolver,
            RateLimiter rateLimiter,
            ApiTokenService apiTokenService,
            Clock clock,
            RateLimitPolicy failedAttemptPolicy,
            boolean rejectEmptyAuthorities
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.scopeResolver = Objects.requireNonNull(scopeResolver);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.apiTokenService = Objects.requireNonNull(apiTokenService);
        this.clock = Objects.requireNonNull(clock);
        this.failedAttemptPolicy = failedAttemptPolicy;
        this.rejectEmptyAuthorities = rejectEmptyAuthorities;
    }

    @Override
    public Optional<AuthenticatedToken> authenticate(RawToken rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        final String attemptKey = FAILED_ATTEMPT_KEY_PREFIX + rawToken.prefix();
        if (isAttemptBudgetExhausted(attemptKey)) {
            // Too many failed attempts for this prefix: reject before paying for a hash verification.
            throw new RateLimitExceededException();
        }
        final Optional<ApiToken> found = repository.findByPrefix(rawToken.prefix());
        if (found.isEmpty()) {
            return rejectAttempt(attemptKey);
        }
        final ApiToken token = found.get();
        if (!token.isUsable()) {
            return rejectAttempt(attemptKey);
        }
        if (token.expiry().isExpired(clock)) {
            return rejectAttempt(attemptKey);
        }
        if (!tokenHasher.matches(rawToken.secret(), token.credentials().tokenHash())) {
            return rejectAttempt(attemptKey);
        }
        if (!rateLimiter.tryAcquire(token.id(), token.rateLimit())) {
            throw new RateLimitExceededException(token.id());
        }
        final Set<String> authorities = scopeResolver.resolve(token.scopes(), token.tenantId());
        if (rejectEmptyAuthorities && authorities.isEmpty()) {
            // The token is valid but grants nothing: fail closed instead of authenticating a no-op principal.
            return Optional.empty();
        }
        return touchLastUsed(token)
                .map(refreshed -> new AuthenticatedToken(refreshed, authorities));
    }

    private Optional<ApiToken> touchLastUsed(ApiToken token) {
        try {
            return Optional.of(apiTokenService.touchLastUsed(token.id()));
        } catch (InvalidTokenStateException | ApiTokenNotFoundException ex) {
            // The token was revoked/deleted between the checks above: fail closed.
            return Optional.empty();
        }
    }

    /**
     * Failed attempts spend the pre-authentication budget; successful ones do not.
     */
    private Optional<AuthenticatedToken> rejectAttempt(String attemptKey) {
        if (failedAttemptPolicy != null && failedAttemptPolicy.isConfigured()) {
            rateLimiter.tryAcquire(attemptKey, failedAttemptPolicy);
        }
        return Optional.empty();
    }

    private boolean isAttemptBudgetExhausted(String attemptKey) {
        return failedAttemptPolicy != null
                && failedAttemptPolicy.isConfigured()
                && rateLimiter.isExhausted(attemptKey, failedAttemptPolicy);
    }
}
