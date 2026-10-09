package ru.openapi.tokens.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;
import ru.openapi.tokens.web.ScopeCatalogController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("OpenApiTokensAutoConfiguration")
class OpenApiTokensAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenApiTokensAutoConfiguration.class))
            .withUserConfiguration(StubPersistenceConfig.class);

    @Test
    @DisplayName("Should not register token beans when disabled")
    void shouldNotRegisterWhenDisabled() {
        runner.withPropertyValues("openapi.tokens.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApiTokenService.class);
                    assertThat(context).doesNotHaveBean(TokenHasher.class);
                    assertThat(context).doesNotHaveBean(OpenApiTokensProperties.class);
                });
    }

    @Test
    @DisplayName("Should register default beans when enabled")
    void shouldRegisterWhenEnabled() {
        runner.withPropertyValues("openapi.tokens.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApiTokenService.class);
                    assertThat(context).hasSingleBean(TokenHasher.class);
                    assertThat(context).hasSingleBean(TokenOwnerResolver.class);
                    assertThat(context).hasSingleBean(OpenApiTokensProperties.class);
                });
    }

    @Test
    @DisplayName("Should register the scope catalog bean built from properties")
    void shouldRegisterScopeCatalog() {
        // Map form avoids treating the ':' inside scope names as a key/value separator
        final Map<String, String> properties = Map.of(
                "openapi.tokens.enabled", "true",
                "openapi.tokens.scopes.catalog[payments:read]", "Чтение платежей",
                "openapi.tokens.scopes.catalog[demo:read]", "Демо-ресурсы",
                "openapi.tokens.scopes.mappings[reporting]", "readReports"
        );
        runner.withInitializer(context -> TestPropertyValues.of(properties).applyTo(context))
                .run(context -> {
                    assertThat(context).hasSingleBean(ScopeCatalog.class);
                    assertThat(context).hasSingleBean(ScopeCatalogController.class);

                    final ScopeCatalog catalog = context.getBean(ScopeCatalog.class);
                    assertThat(catalog.listScopes()).extracting(ScopeInfo::scope)
                            .containsExactly("demo:read", "payments:read", "reporting");
                    assertThat(catalog.listScopes().getFirst().description()).isEqualTo("Демо-ресурсы");
                    assertThat(catalog.listScopes().get(2).projectScopes()).containsExactly("readReports");
                });
    }

    @Test
    @DisplayName("Should return an empty scope catalog when nothing is configured")
    void shouldReturnEmptyScopeCatalog() {
        runner.withPropertyValues("openapi.tokens.enabled=true")
                .run(context -> assertThat(context.getBean(ScopeCatalog.class).listScopes()).isEmpty());
    }

    @Configuration
    static class StubPersistenceConfig {
        @Bean
        ApiTokenRepository apiTokenRepository() {
            return mock(ApiTokenRepository.class);
        }

        @Bean
        ScopeMappingRepository scopeMappingRepository() {
            return mock(ScopeMappingRepository.class);
        }

        @Bean
        AuditLogRepository auditLogRepository() {
            return mock(AuditLogRepository.class);
        }
    }
}
