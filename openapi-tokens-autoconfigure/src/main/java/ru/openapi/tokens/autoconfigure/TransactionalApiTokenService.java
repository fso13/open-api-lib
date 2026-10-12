package ru.openapi.tokens.autoconfigure;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.exception.ConcurrentTokenModificationException;
import ru.openapi.tokens.token.spi.ApiTokenService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transactional decorator around {@link ApiTokenService} (mutations vs reads).
 *
 * <p>Optimistic locking conflicts are translated into a domain exception so that callers (and the
 * REST layer) can answer 409 instead of a generic 500.</p>
 */
@Transactional(readOnly = true)
public class TransactionalApiTokenService implements ApiTokenService {

    private final ApiTokenService delegate;

    public TransactionalApiTokenService(ApiTokenService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public CreatedApiToken create(CreateTokenCommand command) {
        return translateConflicts(() -> delegate.create(command));
    }

    @Override
    public ApiToken get(UUID tokenId) {
        return delegate.get(tokenId);
    }

    @Override
    public List<ApiToken> listOwn() {
        return delegate.listOwn();
    }

    @Override
    @Transactional
    public ApiToken revoke(RevokeTokenCommand command) {
        return translateConflicts(() -> delegate.revoke(command));
    }

    @Override
    @Transactional
    public ApiToken block(BlockTokenCommand command) {
        return translateConflicts(() -> delegate.block(command));
    }

    @Override
    public TokenUsageStats usageStats(UUID tokenId) {
        return delegate.usageStats(tokenId);
    }

    @Override
    public List<ApiToken> listAll() {
        return delegate.listAll();
    }

    @Override
    public List<ApiToken> listAll(int offset, int limit) {
        return delegate.listAll(offset, limit);
    }

    @Override
    public long countAll() {
        return delegate.countAll();
    }

    @Override
    public ApiToken getAny(UUID tokenId) {
        return delegate.getAny(tokenId);
    }

    @Override
    @Transactional
    public ApiToken forceRevoke(RevokeTokenCommand command) {
        return translateConflicts(() -> delegate.forceRevoke(command));
    }

    @Override
    @Transactional
    public ApiToken forceBlock(BlockTokenCommand command) {
        return translateConflicts(() -> delegate.forceBlock(command));
    }

    @Override
    @Transactional
    public ApiToken touchLastUsed(UUID tokenId) {
        return translateConflicts(() -> delegate.touchLastUsed(tokenId));
    }

    private <T> T translateConflicts(Supplier<T> action) {
        try {
            return action.get();
        } catch (OptimisticLockingFailureException ex) {
            throw new ConcurrentTokenModificationException(
                    "Token was modified by another request, retry the operation", ex
            );
        }
    }
}
