package ru.openapi.tokens.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.openapi.tokens.security.ApiTokenAuthenticationFilter;
import ru.openapi.tokens.security.ApiTokenAuthenticationProvider;
import ru.openapi.tokens.security.ApiTokenPermissionEvaluator;
import ru.openapi.tokens.security.DefaultApiTokenAuthenticator;
import ru.openapi.tokens.security.InternalTokenOwnerResolver;
import ru.openapi.tokens.security.KeycloakTokenOwnerResolver;
import ru.openapi.tokens.token.audit.AsyncAuditRecorder;
import ru.openapi.tokens.token.hash.TokenHasherFactory;
import ru.openapi.tokens.token.ratelimit.Bucket4jRateLimiter;
import ru.openapi.tokens.token.scope.CompositeMappingSource;
import ru.openapi.tokens.token.scope.DefaultScopeCatalog;
import ru.openapi.tokens.token.scope.IdentityScopeResolver;
import ru.openapi.tokens.token.scope.MappedScopeResolver;
import ru.openapi.tokens.token.scope.ScopeMappingSource;
import ru.openapi.tokens.token.service.DefaultApiTokenService;
import ru.openapi.tokens.token.spi.ApiTokenAuthenticator;
import ru.openapi.tokens.token.spi.ApiTokenPermissionChecker;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.AuditRecorder;
import ru.openapi.tokens.token.spi.RateLimiter;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeCatalogRepository;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;
import ru.openapi.tokens.token.spi.ScopeResolver;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;
import ru.openapi.tokens.web.AdminApiTokenController;
import ru.openapi.tokens.web.ApiTokenExceptionHandler;
import ru.openapi.tokens.web.ApiTokenWebMapper;
import ru.openapi.tokens.web.ScopeCatalogController;
import ru.openapi.tokens.web.UserApiTokenController;

import java.time.Clock;
import java.util.concurrent.Executor;

@AutoConfiguration
@ConditionalOnProperty(prefix = "openapi.tokens", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiTokensProperties.class)
public class OpenApiTokensAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock openApiTokensClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    TokenHasher tokenHasher(OpenApiTokensProperties properties) {
        return TokenHasherFactory.create(properties.getHashing().getAlgorithm());
    }

    @Bean
    @ConditionalOnMissingBean
    RateLimiter rateLimiter() {
        return new Bucket4jRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    ScopeMappingSource scopeMappingSource(
            OpenApiTokensProperties properties,
            ScopeMappingRepository mappingRepository
    ) {
        return new CompositeMappingSource(properties.getScopes().getMappings(), mappingRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    ScopeResolver scopeResolver(OpenApiTokensProperties properties, ScopeMappingSource scopeMappingSource) {
        if ("mapped".equalsIgnoreCase(properties.getScopes().getMode())) {
            return new MappedScopeResolver(scopeMappingSource);
        }
        return new IdentityScopeResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    ScopeCatalog scopeCatalog(
            OpenApiTokensProperties properties,
            ScopeMappingSource scopeMappingSource,
            ObjectProvider<ScopeCatalogRepository> scopeCatalogRepository
    ) {
        return new DefaultScopeCatalog(
                properties.getScopes().getCatalog(),
                scopeCatalogRepository.getIfAvailable(),
                scopeMappingSource
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openapi.tokens.auth", name = "mode", havingValue = "internal", matchIfMissing = true)
    TokenOwnerResolver internalTokenOwnerResolver() {
        return new InternalTokenOwnerResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openapi.tokens.auth", name = "mode", havingValue = "keycloak")
    @ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
    TokenOwnerResolver keycloakTokenOwnerResolver(OpenApiTokensProperties properties) {
        return new KeycloakTokenOwnerResolver(properties.getAuth().getKeycloak().getTenantClaim());
    }

    @Bean(name = "openapiTokensAuditExecutor")
    @ConditionalOnMissingBean(name = "openapiTokensAuditExecutor")
    Executor openapiTokensAuditExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("openapi-tokens-audit-");
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    AuditRecorder auditRecorder(AuditLogRepository auditLogRepository, Executor openapiTokensAuditExecutor) {
        return new AsyncAuditRecorder(auditLogRepository, openapiTokensAuditExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTokenService apiTokenService(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            TokenOwnerResolver ownerResolver,
            AuditLogRepository auditLogRepository,
            Clock openApiTokensClock,
            OpenApiTokensProperties properties
    ) {
        return new TransactionalApiTokenService(new DefaultApiTokenService(
                repository,
                tokenHasher,
                ownerResolver,
                auditLogRepository,
                openApiTokensClock,
                properties.getToken().getPrefixLength(),
                properties.getLimits().getMaxTokensPerOwner()
        ));
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTokenAuthenticator apiTokenAuthenticator(
            ApiTokenRepository repository,
            TokenHasher tokenHasher,
            ScopeResolver scopeResolver,
            RateLimiter rateLimiter,
            ApiTokenService apiTokenService,
            Clock openApiTokensClock
    ) {
        return new DefaultApiTokenAuthenticator(
                repository, tokenHasher, scopeResolver, rateLimiter, apiTokenService, openApiTokensClock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTokenPermissionChecker apiTokenPermissionChecker() {
        return new ApiTokenPermissionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTokenWebMapper apiTokenWebMapper() {
        return new ApiTokenWebMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    UserApiTokenController userApiTokenController(
            ApiTokenService apiTokenService,
            ApiTokenWebMapper mapper,
            AuditLogRepository auditLogRepository
    ) {
        return new UserApiTokenController(apiTokenService, mapper, auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    AdminApiTokenController adminApiTokenController(
            ApiTokenService apiTokenService,
            ApiTokenWebMapper mapper,
            AuditLogRepository auditLogRepository
    ) {
        return new AdminApiTokenController(apiTokenService, mapper, auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    ScopeCatalogController scopeCatalogController(ScopeCatalog scopeCatalog, ApiTokenWebMapper mapper) {
        return new ScopeCatalogController(scopeCatalog, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTokenExceptionHandler apiTokenExceptionHandler() {
        return new ApiTokenExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTokenAuthenticationFilter apiTokenAuthenticationFilter(
            ApiTokenAuthenticator apiTokenAuthenticator,
            AuditRecorder auditRecorder,
            Clock openApiTokensClock,
            OpenApiTokensProperties properties
    ) {
        // Keep provider/manager local — registering AuthenticationProvider as a bean
        // replaces the global DaoAuthenticationProvider and breaks httpBasic/form login.
        final AuthenticationManager apiTokenAuthenticationManager =
                new ProviderManager(new ApiTokenAuthenticationProvider(apiTokenAuthenticator));
        return new ApiTokenAuthenticationFilter(
                apiTokenAuthenticationManager,
                auditRecorder,
                openApiTokensClock,
                properties.getToken().getHeader(),
                properties.getToken().getBearerPrefix()
        );
    }

    @Bean
    @Order(1)
    @ConditionalOnMissingBean(name = "openapiTokensSecurityFilterChain")
    @ConditionalOnBean(HttpSecurity.class)
    SecurityFilterChain openapiTokensSecurityFilterChain(
            HttpSecurity http,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter
    ) throws Exception {
        return http
                .securityMatcher("/api/openapi/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/openapi/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
