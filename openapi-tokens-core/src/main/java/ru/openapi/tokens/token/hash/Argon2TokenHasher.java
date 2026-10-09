package ru.openapi.tokens.token.hash;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import ru.openapi.tokens.token.spi.TokenHasher;

import java.util.Objects;

/**
 * Argon2id-based {@link TokenHasher} (default).
 */
public final class Argon2TokenHasher implements TokenHasher {

    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536;
    private static final int PARALLELISM = 1;

    private final Argon2 argon2;

    public Argon2TokenHasher() {
        this.argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    }

    @Override
    public String hash(String rawSecret) {
        Objects.requireNonNull(rawSecret, "rawSecret");
        final char[] chars = rawSecret.toCharArray();
        try {
            return argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, chars);
        } finally {
            argon2.wipeArray(chars);
        }
    }

    @Override
    public boolean matches(String rawSecret, String tokenHash) {
        Objects.requireNonNull(rawSecret, "rawSecret");
        Objects.requireNonNull(tokenHash, "tokenHash");
        final char[] chars = rawSecret.toCharArray();
        try {
            return argon2.verify(tokenHash, chars);
        } finally {
            argon2.wipeArray(chars);
        }
    }
}
