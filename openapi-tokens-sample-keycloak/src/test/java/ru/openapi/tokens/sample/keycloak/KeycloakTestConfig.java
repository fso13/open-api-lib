package ru.openapi.tokens.sample.keycloak;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import ru.openapi.tokens.autoconfigure.OpenApiTokensProperties;
import ru.openapi.tokens.sample.ui.TokenUiMapper;
import ru.openapi.tokens.security.ApiTokenAuthenticationFilter;
import ru.openapi.tokens.security.ApiTokenAuthenticationProvider;
import ru.openapi.tokens.security.KeycloakTokenOwnerResolver;
import ru.openapi.tokens.token.service.DefaultApiTokenService;
import ru.openapi.tokens.token.spi.ApiTokenAuthenticator;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.AuditRecorder;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;
import ru.openapi.tokens.token.spi.TokenHasher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Test doubles for the Keycloak slices: no running Keycloak, no database — the real starter service
 * and the real {@link KeycloakTokenOwnerResolver} run against mocked repositories.
 */
@TestConfiguration
public class KeycloakTestConfig {

    public static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Bean
    ApiTokenRepository apiTokenRepository() {
        return mock(ApiTokenRepository.class);
    }

    @Bean
    AuditLogRepository auditLogRepository() {
        return mock(AuditLogRepository.class);
    }

    @Bean
    ScopeMappingRepository scopeMappingRepository() {
        return mock(ScopeMappingRepository.class);
    }

    @Bean
    TokenHasher tokenHasher() {
        return mock(TokenHasher.class);
    }

    @Bean
    ScopeCatalog scopeCatalog() {
        return List::of;
    }

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    @Bean
    OpenApiTokensProperties openApiTokensProperties() {
        final OpenApiTokensProperties properties = new OpenApiTokensProperties();
        properties.getAuth().setMode("keycloak");
        properties.getAuth().getKeycloak().setTenantClaim("tenant_id");
        return properties;
    }

    @Bean
    ApiTokenService apiTokenService(
            ApiTokenRepository apiTokenRepository,
            TokenHasher tokenHasher,
            AuditLogRepository auditLogRepository,
            Clock clock,
            OpenApiTokensProperties properties
    ) {
        return new DefaultApiTokenService(
                apiTokenRepository,
                tokenHasher,
                new KeycloakTokenOwnerResolver(properties.getAuth().getKeycloak().getTenantClaim()),
                auditLogRepository,
                clock,
                properties.getToken().getPrefixLength(),
                properties.getLimits().getMaxTokensPerOwner()
        );
    }

    @Bean
    ApiTokenAuthenticator apiTokenAuthenticator() {
        return mock(ApiTokenAuthenticator.class);
    }

    @Bean
    AuditRecorder auditRecorder() {
        return mock(AuditRecorder.class);
    }

    /**
     * Same construction as the starter's autoconfiguration (the slice does not run it).
     */
    @Bean
    ApiTokenAuthenticationFilter apiTokenAuthenticationFilter(
            ApiTokenAuthenticator apiTokenAuthenticator,
            AuditRecorder auditRecorder,
            Clock clock,
            OpenApiTokensProperties properties
    ) {
        return new ApiTokenAuthenticationFilter(
                new ProviderManager(new ApiTokenAuthenticationProvider(apiTokenAuthenticator)),
                auditRecorder,
                clock,
                properties.getToken().getHeader(),
                properties.getToken().getBearerPrefix()
        );
    }

    @Bean
    TokenUiMapper tokenUiMapper(Clock clock) {
        return new TokenUiMapper(clock);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration
                .withRegistrationId("keycloak")
                .clientId("openapi-tokens-ui")
                .clientSecret("ui-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .issuerUri("http://localhost:8180/realms/openapi-tokens")
                .authorizationUri("http://localhost:8180/realms/openapi-tokens/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8180/realms/openapi-tokens/protocol/openid-connect/token")
                .jwkSetUri("http://localhost:8180/realms/openapi-tokens/protocol/openid-connect/certs")
                .userInfoUri("http://localhost:8180/realms/openapi-tokens/protocol/openid-connect/userinfo")
                .userNameAttributeName("sub")
                .clientName("Keycloak")
                .build());
    }
}
