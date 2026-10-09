package ru.openapi.tokens.token;

import ru.openapi.tokens.token.exception.InvalidTokenFormatException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Raw bearer token shown once to the client: {@code atk_<prefix>_<secret>}.
 */
public record RawToken(String prefix, String secret) {

    public static final String SCHEME = "atk";
    private static final int DEFAULT_SECRET_BYTES = 32;
    private static final int MIN_PREFIX_LENGTH = 4;
    private static final int MAX_PREFIX_LENGTH = 16;
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "^" + SCHEME + "_([A-Za-z0-9]{" + MIN_PREFIX_LENGTH + "," + MAX_PREFIX_LENGTH + "})_([A-Za-z0-9_-]+)$"
    );
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public RawToken {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(secret, "secret");
        if (prefix.isBlank() || secret.isBlank()) {
            throw new InvalidTokenFormatException("prefix and secret must not be blank");
        }
    }

    public String value() {
        return SCHEME + "_" + prefix + "_" + secret;
    }

    public static RawToken parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTokenFormatException("raw token must not be blank");
        }
        final Matcher matcher = TOKEN_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new InvalidTokenFormatException("expected format atk_<prefix>_<secret>");
        }
        return new RawToken(matcher.group(1), matcher.group(2));
    }

    public static RawToken generate(int prefixLength) {
        if (prefixLength < MIN_PREFIX_LENGTH || prefixLength > MAX_PREFIX_LENGTH) {
            throw new IllegalArgumentException(
                    "prefixLength must be between %d and %d".formatted(MIN_PREFIX_LENGTH, MAX_PREFIX_LENGTH)
            );
        }
        final String prefix = randomHex(prefixLength);
        final String secret = randomHex(DEFAULT_SECRET_BYTES * 2);
        return new RawToken(prefix, secret);
    }

    private static String randomHex(int length) {
        final byte[] bytes = new byte[(length + 1) / 2];
        SECURE_RANDOM.nextBytes(bytes);
        final String hex = HexFormat.of().formatHex(bytes);
        return hex.substring(0, length);
    }
}
