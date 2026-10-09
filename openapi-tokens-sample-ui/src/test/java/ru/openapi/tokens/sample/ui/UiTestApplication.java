package ru.openapi.tokens.sample.ui;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import ru.openapi.tokens.autoconfigure.OpenApiTokensProperties;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Test bootstrap for the UI slices (the module itself is a library, not an application).
 *
 * <p>Shared infrastructure and the exception/UI advice are imported explicitly: component scanning
 * cannot be used here because the module also contains the test configurations of its sibling test
 * classes. Each slice imports the controller under test itself.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        UiModelAdvice.class,
        UiExceptionAdvice.class,
        UiTestSecurityConfig.class
})
class UiTestApplication {

    @Bean
    Clock clock() {
        return Clock.fixed(UiTestTokens.NOW, ZoneId.of("UTC"));
    }

    @Bean
    OpenApiTokensProperties openApiTokensProperties() {
        return new OpenApiTokensProperties();
    }

    @Bean
    TokenUiMapper tokenUiMapper(Clock clock) {
        return new TokenUiMapper(clock);
    }
}
