package ru.openapi.tokens.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.exception.InvalidScopeException;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;
import ru.openapi.tokens.token.spi.ScopeValidator;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;
import ru.openapi.tokens.web.ScopeCatalogController;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("Should reject denied and unknown scopes with the default catalog mode")
    void shouldValidateScopesByDefault() {
        // Map form avoids treating the ':' inside scope names as a key/value separator
        final Map<String, String> properties = Map.of(
                "openapi.tokens.enabled", "true",
                "openapi.tokens.scopes.catalog[payments:read]", "Чтение платежей"
        );
        runner.withInitializer(context -> TestPropertyValues.of(properties).applyTo(context))
                .run(context -> {
                    assertThat(context).hasSingleBean(ScopeValidator.class);
                    final ScopeValidator validator = context.getBean(ScopeValidator.class);

                    assertThatThrownBy(() -> validator.validate(context(Set.of("ROLE_ADMIN"))))
                            .isInstanceOf(InvalidScopeException.class)
                            .satisfies(ex -> assertThat(((InvalidScopeException) ex).getReason())
                                    .isEqualTo(InvalidScopeException.Reason.DENIED_SCOPE));
                    assertThatThrownBy(() -> validator.validate(context(Set.of("super:scope"))))
                            .isInstanceOf(InvalidScopeException.class)
                            .satisfies(ex -> assertThat(((InvalidScopeException) ex).getReason())
                                    .isEqualTo(InvalidScopeException.Reason.UNKNOWN_SCOPE));

                    assertThatCode(() -> validator.validate(context(Set.of("payments:read"))))
                            .doesNotThrowAnyException();
                });
    }

    @Test
    @DisplayName("Should accept anything when scope validation is off")
    void shouldAcceptAnythingWhenValidationOff() {
        runner.withPropertyValues(
                        "openapi.tokens.enabled=true",
                        "openapi.tokens.scopes.validation.mode=off")
                .run(context -> assertThatCode(() ->
                        context.getBean(ScopeValidator.class).validate(context(Set.of("ROLE_ADMIN"))))
                        .doesNotThrowAnyException());
    }

    @Test
    @DisplayName("Should fail closed in strict mode without caller authorities")
    void shouldFailClosedInStrictMode() {
        final Map<String, String> properties = Map.of(
                "openapi.tokens.enabled", "true",
                "openapi.tokens.scopes.validation.mode", "strict",
                "openapi.tokens.scopes.catalog[payments:read]", "Чтение платежей"
        );
        runner.withInitializer(context -> TestPropertyValues.of(properties).applyTo(context))
                .run(context -> assertThatThrownBy(() ->
                        context.getBean(ScopeValidator.class).validate(context(Set.of("payments:read"))))
                        .isInstanceOf(InvalidScopeException.class)
                        .satisfies(ex -> assertThat(((InvalidScopeException) ex).getReason())
                                .isEqualTo(InvalidScopeException.Reason.NOT_GRANTABLE)));
    }

    @Test
    @DisplayName("Should fail to start on an unknown scopes.mode value")
    void shouldFailOnUnknownScopeMode() {
        runner.withPropertyValues(
                        "openapi.tokens.enabled=true",
                        "openapi.tokens.scopes.mode=mappd")
                .run(context -> assertThat(context).hasFailed());
    }

    private static ScopeValidationContext context(Set<String> requested) {
        return new ScopeValidationContext("owner-1", null, requested, Set.of(), false);
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
