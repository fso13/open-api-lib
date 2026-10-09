package ru.openapi.tokens.token.hash;

import ru.openapi.tokens.token.spi.TokenHasher;

import java.util.Objects;

/**
 * Creates {@link TokenHasher} implementations by algorithm.
 */
public final class TokenHasherFactory {

    private TokenHasherFactory() {
    }

    public static TokenHasher create(HashingAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        return switch (algorithm) {
            case ARGON2 -> new Argon2TokenHasher();
            case BCRYPT -> new BcryptTokenHasher();
        };
    }
}
