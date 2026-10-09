package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.RawToken;

import java.util.Optional;
import java.util.Set;

/**
 * Authenticates a raw API token into a domain principal snapshot.
 */
public interface ApiTokenAuthenticator {

    Optional<AuthenticatedToken> authenticate(RawToken rawToken);

    /**
     * Authenticated token principal with resolved project authorities.
     */
    record AuthenticatedToken(ApiToken token, Set<String> authorities) {
    }
}
