package ru.openapi.tokens.token.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.openapi.tokens.token.spi.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenHasher")
class TokenHasherTest {

    @ParameterizedTest
    @EnumSource(HashingAlgorithm.class)
    @DisplayName("Should roundtrip hash and matches for each algorithm")
    void shouldRoundtrip(HashingAlgorithm algorithm) {
        final TokenHasher hasher = TokenHasherFactory.create(algorithm);
        final String secret = "super-secret-value";

        final String hash = hasher.hash(secret);

        assertThat(hash).isNotBlank().isNotEqualTo(secret);
        assertThat(hasher.matches(secret, hash)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(HashingAlgorithm.class)
    @DisplayName("Should not match different secret")
    void shouldNotMatchMismatch(HashingAlgorithm algorithm) {
        final TokenHasher hasher = TokenHasherFactory.create(algorithm);
        final String hash = hasher.hash("correct-secret");

        assertThat(hasher.matches("wrong-secret", hash)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(HashingAlgorithm.class)
    @DisplayName("Should produce different hashes for same secret (salted)")
    void shouldSaltHashes(HashingAlgorithm algorithm) {
        final TokenHasher hasher = TokenHasherFactory.create(algorithm);
        final String secret = "same-secret";

        assertThat(hasher.hash(secret)).isNotEqualTo(hasher.hash(secret));
    }

    @Nested
    @DisplayName("factory")
    class Factory {

        @Test
        @DisplayName("Should default to Argon2")
        void shouldDefaultToArgon2() {
            final TokenHasher hasher = TokenHasherFactory.create(HashingAlgorithm.ARGON2);
            assertThat(hasher).isInstanceOf(Argon2TokenHasher.class);
        }

        @Test
        @DisplayName("Should create BCrypt hasher")
        void shouldCreateBcrypt() {
            assertThat(TokenHasherFactory.create(HashingAlgorithm.BCRYPT))
                    .isInstanceOf(BcryptTokenHasher.class);
        }

        @Test
        @DisplayName("Should reject null algorithm")
        void shouldRejectNull() {
            assertThatThrownBy(() -> TokenHasherFactory.create(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
