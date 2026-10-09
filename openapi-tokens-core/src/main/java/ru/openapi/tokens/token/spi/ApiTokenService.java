package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;

import java.util.List;
import java.util.UUID;

/**
 * Application service port for API token lifecycle.
 */
public interface ApiTokenService {

    CreatedApiToken create(CreateTokenCommand command);

    ApiToken get(UUID tokenId);

    List<ApiToken> listOwn();

    ApiToken revoke(RevokeTokenCommand command);

    ApiToken block(BlockTokenCommand command);

    TokenUsageStats usageStats(UUID tokenId);

    /**
     * Admin: list all tokens (no ownership filter).
     */
    List<ApiToken> listAll();

    /**
     * Admin: get token by id without ownership check.
     */
    ApiToken getAny(UUID tokenId);

    /**
     * Admin: revoke any token.
     */
    ApiToken forceRevoke(RevokeTokenCommand command);

    /**
     * Admin: block/unblock any token.
     */
    ApiToken forceBlock(BlockTokenCommand command);

    /**
     * Updates sliding TTL anchor after successful authentication.
     */
    ApiToken touchLastUsed(UUID tokenId);
}
