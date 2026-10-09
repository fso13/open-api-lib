package ru.openapi.tokens.token.spi;

/**
 * One-way hashing of raw token secrets.
 */
public interface TokenHasher {

    String hash(String rawSecret);

    boolean matches(String rawSecret, String tokenHash);
}
