package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.TokenOwner;

/**
 * Resolves the current token owner from the security context.
 */
public interface TokenOwnerResolver {

    TokenOwner resolveCurrentOwner();
}
