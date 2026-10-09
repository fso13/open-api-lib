package ru.openapi.tokens.sample.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import ru.openapi.tokens.sample.ui.TokenUiSecurity;
import ru.openapi.tokens.security.ApiTokenAuthenticationFilter;
import ru.openapi.tokens.token.RawToken;

/**
 * Keycloak wiring for the sample:
 *
 * <ol>
 *     <li>{@code /api/openapi/**} — stateless chain that accepts <em>both</em> Keycloak access tokens
 *     (OAuth2 resource server) and API tokens issued by this service;</li>
 *     <li>UI — authorization code flow login at Keycloak ({@code oauth2Login}) on top of the shared
 *     {@link TokenUiSecurity} rules;</li>
 *     <li>{@code /api/demo/**} — stateless chain for the demo resources, same two token types.</li>
 * </ol>
 *
 * <p>The first chain <em>replaces</em> the starter's {@code openapiTokensSecurityFilterChain}
 * (same bean name): the default one only knows HTTP Basic and would reject Keycloak JWTs.</p>
 *
 * <p>API tokens ({@code atk_…}) are not JWTs, so the resource server's bearer resolver is wrapped to
 * ignore them — otherwise it would try to decode them and answer 401 before authorization.</p>
 */
@Configuration
@EnableMethodSecurity
public class KeycloakSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain openapiTokensSecurityFilterChain(
            HttpSecurity http,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
            Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakJwtAuthenticationConverter
    ) throws Exception {
        return statelessBearerChain(http, apiTokenAuthenticationFilter, keycloakJwtAuthenticationConverter)
                .securityMatcher("/api/openapi/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/openapi/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    @Order(TokenUiSecurity.UI_FILTER_CHAIN_ORDER)
    SecurityFilterChain keycloakUiSecurityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrations,
            KeycloakGrantedAuthoritiesMapper keycloakAuthoritiesMapper,
            OAuth2UserService<OidcUserRequest, OidcUser> keycloakOidcUserService
    ) throws Exception {
        return TokenUiSecurity.uiChain(http, "/login", "/oauth2/**", "/login/oauth2/**")
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(keycloakOidcUserService)
                                .userAuthoritiesMapper(keycloakAuthoritiesMapper))
                        .defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutSuccessHandler(keycloakLogoutSuccessHandler(clientRegistrations)))
                .build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain demoApiSecurityFilterChain(
            HttpSecurity http,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
            Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakJwtAuthenticationConverter
    ) throws Exception {
        return statelessBearerChain(http, apiTokenAuthenticationFilter, keycloakJwtAuthenticationConverter)
                .securityMatcher("/api/demo/**", "/actuator/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    /**
     * Principal name of an interactive login is {@code preferred_username} instead of the
     * {@code sub} UUID, so that the UI header shows a human readable user name.
     */
    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> keycloakOidcUserService() {
        final OidcUserService delegate = new OidcUserService();
        return userRequest -> {
            final OidcUser user = delegate.loadUser(userRequest);
            return user.getClaimAsString("preferred_username") == null
                    ? user
                    : new DefaultOidcUser(user.getAuthorities(), user.getIdToken(), user.getUserInfo(),
                            "preferred_username");
        };
    }

    /**
     * Keycloak realm/client roles → {@code ROLE_*} authorities for interactive OIDC logins.
     */
    @Bean
    KeycloakGrantedAuthoritiesMapper keycloakGrantedAuthoritiesMapper() {
        return new KeycloakGrantedAuthoritiesMapper();
    }

    /**
     * Keycloak access token → authentication (scopes plus {@code ROLE_*} realm/client roles).
     */
    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    /**
     * RP-initiated logout: closes the Keycloak SSO session too, otherwise the next request would
     * silently log the user in again.
     */
    private static LogoutSuccessHandler keycloakLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrations
    ) {
        final OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    private static HttpSecurity statelessBearerChain(
            HttpSecurity http,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
            Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakJwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(apiTokenAwareBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
                .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }

    /**
     * Keycloak JWTs go to the resource server, API tokens ({@code atk_…}) are handled by
     * {@link ApiTokenAuthenticationFilter}.
     */
    private static BearerTokenResolver apiTokenAwareBearerTokenResolver() {
        final BearerTokenResolver jwtsOnly = new DefaultBearerTokenResolver();
        return request -> {
            final String token = jwtsOnly.resolve(request);
            return token != null && token.startsWith(RawToken.SCHEME + "_") ? null : token;
        };
    }
}
