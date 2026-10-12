package ru.openapi.tokens.token.hash;

import ru.openapi.tokens.token.spi.TokenHasher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Algorithm-tagged {@link TokenHasher}: new hashes are written as {@code {algorithm}<native-hash>}
 * and verification dispatches on the tag.
 *
 * <p>This makes {@code openapi.tokens.hashing.algorithm} changeable without invalidating every
 * stored token: switches only affect newly created hashes, existing ones keep verifying with the
 * algorithm they were created with (including legacy untagged hashes, which are detected from their
 * native prefix).</p>
 */
public final class AlgorithmTaggedTokenHasher implements TokenHasher {

    private final HashingAlgorithm primary;
    private final Map<HashingAlgorithm, TokenHasher> hashers;

    public AlgorithmTaggedTokenHasher(HashingAlgorithm primary) {
        this(primary, all());
    }

    AlgorithmTaggedTokenHasher(HashingAlgorithm primary, Map<HashingAlgorithm, TokenHasher> hashers) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.hashers = Map.copyOf(hashers);
    }

    private static Map<HashingAlgorithm, TokenHasher> all() {
        final Map<HashingAlgorithm, TokenHasher> map = new LinkedHashMap<>();
        for (HashingAlgorithm algorithm : HashingAlgorithm.values()) {
            map.put(algorithm, TokenHasherFactory.create(algorithm));
        }
        return map;
    }

    @Override
    public String hash(String rawSecret) {
        Objects.requireNonNull(rawSecret, "rawSecret");
        return "{" + tag(primary) + "}" + hasher(primary).hash(rawSecret);
    }

    @Override
    public boolean matches(String rawSecret, String tokenHash) {
        Objects.requireNonNull(rawSecret, "rawSecret");
        Objects.requireNonNull(tokenHash, "tokenHash");
        final TaggedHash tagged = TaggedHash.parse(tokenHash);
        if (tagged != null) {
            final TokenHasher hasher = hashers.get(tagged.algorithm());
            return hasher != null && hasher.matches(rawSecret, tagged.hash());
        }
        // Legacy untagged hash: detect the algorithm from its native prefix.
        final HashingAlgorithm detected = detect(tokenHash);
        if (detected != null) {
            return hasher(detected).matches(rawSecret, tokenHash);
        }
        return hasher(primary).matches(rawSecret, tokenHash);
    }

    private TokenHasher hasher(HashingAlgorithm algorithm) {
        final TokenHasher hasher = hashers.get(algorithm);
        if (hasher == null) {
            throw new IllegalStateException("No TokenHasher registered for " + algorithm);
        }
        return hasher;
    }

    private static String tag(HashingAlgorithm algorithm) {
        return algorithm.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static HashingAlgorithm detect(String tokenHash) {
        if (tokenHash.startsWith("$argon2")) {
            return HashingAlgorithm.ARGON2;
        }
        if (tokenHash.startsWith("$2a$") || tokenHash.startsWith("$2b$") || tokenHash.startsWith("$2y$")) {
            return HashingAlgorithm.BCRYPT;
        }
        return null;
    }

    private record TaggedHash(HashingAlgorithm algorithm, String hash) {

        private static TaggedHash parse(String tokenHash) {
            if (!tokenHash.startsWith("{")) {
                return null;
            }
            final int end = tokenHash.indexOf('}');
            if (end < 0) {
                return null;
            }
            final String name = tokenHash.substring(1, end);
            for (HashingAlgorithm algorithm : HashingAlgorithm.values()) {
                if (algorithm.name().equalsIgnoreCase(name)) {
                    return new TaggedHash(algorithm, tokenHash.substring(end + 1));
                }
            }
            return null;
        }
    }
}
