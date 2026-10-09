package ru.openapi.tokens;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gradle multi-module smoke")
class OpenApiTokensSmokeTest {

    @Test
    @DisplayName("Should expose library artifact prefix")
    void shouldExposeArtifactPrefix() {
        assertThat(OpenApiTokens.ARTIFACT_PREFIX).isEqualTo("openapi-tokens");
    }
}
