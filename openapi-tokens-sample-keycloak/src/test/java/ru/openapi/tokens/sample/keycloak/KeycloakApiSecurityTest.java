package ru.openapi.tokens.sample.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.web.AdminApiTokenController;
import ru.openapi.tokens.web.ApiTokenExceptionHandler;
import ru.openapi.tokens.web.ApiTokenWebMapper;
import ru.openapi.tokens.web.ScopeCatalogController;
import ru.openapi.tokens.web.UserApiTokenController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The token API must accept Keycloak access tokens and resolve the owner from the {@code sub} claim.
 */
@WebMvcTest(controllers = {
        UserApiTokenController.class,
        AdminApiTokenController.class,
        ScopeCatalogController.class,
        KeycloakDemoController.class
})
@Import({
        KeycloakSecurityConfig.class,
        KeycloakTestConfig.class,
        ApiTokenWebMapper.class,
        ApiTokenExceptionHandler.class,
        UserApiTokenController.class,
        AdminApiTokenController.class,
        ScopeCatalogController.class
})
@DisplayName("Keycloak-secured token API")
class KeycloakApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @BeforeEach
    void setUp() {
        when(tokenHasher.hash(anyString())).thenReturn("$test$hash");
        when(apiTokenRepository.countByOwnerId(anyString())).thenReturn(0L);
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("POST /api/openapi/tokens creates a token owned by the Keycloak sub")
    void createsTokenForKeycloakOwner() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("kc-user-1")
                                .claim("tenant_id", "tenant-a")
                                .claim("preferred_username", "demo")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ci-bot","scopes":["payments:read"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token.ownerId").value("kc-user-1"))
                .andExpect(jsonPath("$.token.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.rawToken").exists());
    }

    @Test
    @DisplayName("GET /api/openapi/tokens requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/openapi/tokens"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/openapi/tokens lists the token of the Keycloak owner")
    void listsOwnTokens() throws Exception {
        when(apiTokenRepository.findByOwnerId("kc-user-1")).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/openapi/tokens")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/openapi/scopes is available to any Keycloak user")
    void exposesScopeCatalog() throws Exception {
        mockMvc.perform(get("/api/openapi/scopes")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/openapi/admin/tokens requires the ADMIN realm role")
    void adminEndpointRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/openapi/admin/tokens")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        when(apiTokenRepository.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/openapi/admin/tokens")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/demo/whoami exposes the Keycloak identity")
    void whoamiExposesKeycloakClaims() throws Exception {
        mockMvc.perform(get("/api/demo/whoami")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("kc-user-1")
                                .claim("preferred_username", "demo")
                                .claim("tenant_id", "tenant-a"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("keycloak-access-token"))
                .andExpect(jsonPath("$.sub").value("kc-user-1"))
                .andExpect(jsonPath("$.preferredUsername").value("demo"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }

    @Test
    @DisplayName("GET /api/demo/payments needs the API token scope, not a Keycloak role")
    void paymentsNeedsApiTokenScope() throws Exception {
        mockMvc.perform(get("/api/demo/payments")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/demo/payments")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("payments:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("granted"));
    }

    @Test
    @DisplayName("GET /api/demo/reports needs the ADMIN realm role")
    void reportsNeedAdminRole() throws Exception {
        mockMvc.perform(get("/api/demo/reports")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/demo/reports")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedBy").value("keycloak realm role ADMIN"));
    }
}
