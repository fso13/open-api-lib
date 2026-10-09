package ru.openapi.tokens.token.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable command to revoke an API token.
 */
public record RevokeTokenCommand(UUID tokenId) {

    public RevokeTokenCommand {
        Objects.requireNonNull(tokenId, "tokenId");
    }
}
