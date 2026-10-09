package ru.openapi.tokens.token.hash;

import at.favre.lib.crypto.bcrypt.BCrypt;
import ru.openapi.tokens.token.spi.TokenHasher;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * BCrypt-based {@link TokenHasher} alternative.
 */
public final class BcryptTokenHasher implements TokenHasher {

    private static final int COST = 12;

    @Override
    public String hash(String rawSecret) {
        Objects.requireNonNull(rawSecret, "rawSecret");
        return BCrypt.withDefaults().hashToString(COST, rawSecret.toCharArray());
    }

    @Override
    public boolean matches(String rawSecret, String tokenHash) {
        Objects.requireNonNull(rawSecret, "rawSecret");
        Objects.requireNonNull(tokenHash, "tokenHash");
        final BCrypt.Result result = BCrypt.verifyer()
                .verify(rawSecret.getBytes(StandardCharsets.UTF_8), tokenHash.getBytes(StandardCharsets.UTF_8));
        return result.verified;
    }
}
