package ru.openapi.tokens.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.openapi.tokens.token.exception.InvalidTokenFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RawToken format")
class RawTokenFormatTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("Should parse valid atk_prefix_secret token")
        void shouldParseValidToken() {
            final RawToken raw = RawToken.parse("atk_abcd1234_supersecretvalue");

            assertThat(raw.prefix()).isEqualTo("abcd1234");
            assertThat(raw.secret()).isEqualTo("supersecretvalue");
            assertThat(raw.value()).isEqualTo("atk_abcd1234_supersecretvalue");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                " ",
                "atk_",
                "atk_onlyprefix",
                "atk__secret",
                "bearer_abcd1234_secret",
                "atk_ab_secret",
                "atk_toolongprefix12345_secret"
        })
        @DisplayName("Should reject invalid formats")
        void shouldRejectInvalidFormats(String value) {
            assertThatThrownBy(() -> RawToken.parse(value))
                    .isInstanceOf(InvalidTokenFormatException.class);
        }
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("Should generate token with configured prefix length")
        void shouldGenerateWithPrefixLength() {
            final RawToken raw = RawToken.generate(8);

            assertThat(raw.prefix()).hasSize(8);
            assertThat(raw.secret()).isNotBlank();
            assertThat(raw.value()).startsWith("atk_").contains("_");
            assertThat(RawToken.parse(raw.value())).isEqualTo(raw);
        }

        @Test
        @DisplayName("Should reject non-positive prefix length")
        void shouldRejectInvalidPrefixLength() {
            assertThatThrownBy(() -> RawToken.generate(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
