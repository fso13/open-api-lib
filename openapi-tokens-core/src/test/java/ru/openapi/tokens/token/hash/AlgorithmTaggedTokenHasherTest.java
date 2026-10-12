package ru.openapi.tokens.token.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.openapi.tokens.token.spi.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AlgorithmTaggedTokenHasher")
class AlgorithmTaggedTokenHasherTest {

    private static final String SECRET = "super-secret-value";

    @Test
    @DisplayName("Should tag new hashes with the configured algorithm")
    void shouldTagHashes() {
        final TokenHasher hasher = TokenHasherFactory.createTagged(HashingAlgorithm.ARGON2);

        final String hash = hasher.hash(SECRET);

        assertThat(hash).startsWith("{argon2}$argon2");
        assertThat(hasher.matches(SECRET, hash)).isTrue();
    }

    @Test
    @DisplayName("Should verify hashes created by another algorithm (safe algorithm switch)")
    void shouldVerifyOtherAlgorithms() {
        final String bcryptHash = TokenHasherFactory.createTagged(HashingAlgorithm.BCRYPT).hash(SECRET);
        final String argonHash = TokenHasherFactory.createTagged(HashingAlgorithm.ARGON2).hash(SECRET);

        // Verification must not depend on the currently configured algorithm.
        assertThat(TokenHasherFactory.createTagged(HashingAlgorithm.ARGON2).matches(SECRET, bcryptHash)).isTrue();
        assertThat(TokenHasherFactory.createTagged(HashingAlgorithm.BCRYPT).matches(SECRET, argonHash)).isTrue();
    }

    @Test
    @DisplayName("Should verify legacy untagged hashes by their native prefix")
    void shouldVerifyLegacyHashes() {
        final String legacyBcrypt = TokenHasherFactory.create(HashingAlgorithm.BCRYPT).hash(SECRET);
        final String legacyArgon = TokenHasherFactory.create(HashingAlgorithm.ARGON2).hash(SECRET);
        final TokenHasher hasher = TokenHasherFactory.createTagged(HashingAlgorithm.ARGON2);

        assertThat(hasher.matches(SECRET, legacyBcrypt)).isTrue();
        assertThat(hasher.matches(SECRET, legacyArgon)).isTrue();
    }

    @Test
    @DisplayName("Should reject a wrong secret or an unparsable hash")
    void shouldRejectInvalid() {
        final TokenHasher hasher = TokenHasherFactory.createTagged(HashingAlgorithm.ARGON2);
        final String hash = hasher.hash(SECRET);

        assertThat(hasher.matches("other-secret", hash)).isFalse();
        assertThat(hasher.matches(SECRET, "{unknown}whatever")).isFalse();
    }
}
