package ru.openapi.tokens.token.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable command to block or unblock an API token.
 */
public record BlockTokenCommand(UUID tokenId, boolean block) {

    public BlockTokenCommand {
        Objects.requireNonNull(tokenId, "tokenId");
    }

    public static BlockTokenCommand block(UUID tokenId) {
        return new BlockTokenCommand(tokenId, true);
    }

    public static BlockTokenCommand unblock(UUID tokenId) {
        return new BlockTokenCommand(tokenId, false);
    }
}
