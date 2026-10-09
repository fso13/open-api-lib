package ru.openapi.tokens.autoconfigure;

import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.spi.ApiTokenService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Transactional decorator around {@link ApiTokenService} (mutations vs reads).
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
        return delegate.create(command);
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
        return delegate.revoke(command);
    }

    @Override
    @Transactional
    public ApiToken block(BlockTokenCommand command) {
        return delegate.block(command);
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
    public ApiToken getAny(UUID tokenId) {
        return delegate.getAny(tokenId);
    }

    @Override
    @Transactional
    public ApiToken forceRevoke(RevokeTokenCommand command) {
        return delegate.forceRevoke(command);
    }

    @Override
    @Transactional
    public ApiToken forceBlock(BlockTokenCommand command) {
        return delegate.forceBlock(command);
    }

    @Override
    @Transactional
    public ApiToken touchLastUsed(UUID tokenId) {
        return delegate.touchLastUsed(tokenId);
    }
}
