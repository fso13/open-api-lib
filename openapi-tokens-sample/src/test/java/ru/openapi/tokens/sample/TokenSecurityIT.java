package ru.openapi.tokens.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression tests for the security hardening: scope validation, tenant policy, input
 * validation, admin authorization and the fail-closed error contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Token security hardening")
class TokenSecurityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("openapi_tokens")
            .withUsername("openapi")
            .withPassword("openapi");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should refuse to mint a token carrying an admin role (privilege escalation)")
    void shouldRefuseAdminRoleScope() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"escalation","scopes":["ROLE_ADMIN"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Invalid Scopes"))
                .andExpect(jsonPath("$.reason").value("DENIED_SCOPE"))
                .andExpect(jsonPath("$.invalidScopes[0]").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Should refuse a scope that is not part of the catalog")
    void shouldRefuseUnknownScope() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"garbage","scopes":["super:scope"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("UNKNOWN_SCOPE"))
                .andExpect(jsonPath("$.invalidScopes[0]").value("super:scope"));
    }

    @Test
    @DisplayName("Should still create a catalog scope and use it against the demo API")
    void shouldCreateCatalogScope() throws Exception {
        final MvcResult created = mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ci","scopes":["payments:read"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token.slidingTtlSeconds").doesNotExist())
                .andReturn();

        final String rawToken = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("rawToken").asText();

        mockMvc.perform(get("/api/demo/payments").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("granted"));

        // The scope did not grant admin rights.
        mockMvc.perform(get("/api/openapi/admin/tokens").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should not let a user write a token into another tenant")
    void shouldRefuseForeignTenant() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"tenant-spoof","scopes":["payments:read"],"tenantId":"tenant-a"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Tenant Not Allowed"));
    }

    @Test
    @DisplayName("Should reject invalid rate limit values instead of storing a broken token")
    void shouldRejectInvalidRateLimit() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"broken","scopes":["payments:read"],
                                 "rateLimitRequests":0,"rateLimitWindowSeconds":60}
                                """))
                .andExpect(status().isBadRequest());

        // Half configured limit is refused as well.
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"half","scopes":["payments:read"],"rateLimitRequests":100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject an oversized tenant id")
    void shouldRejectOversizedTenantId() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"long","scopes":["payments:read"],"tenantId":"%s"}
                                """.formatted("t".repeat(129))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject a mistyped payload instead of silently ignoring the field")
    void shouldRejectUnknownProperty() throws Exception {
        mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"typo","scopes":["payments:read"],"expireAt":"2030-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should keep the admin API admin-only")
    void shouldProtectAdminApi() throws Exception {
        mockMvc.perform(get("/api/openapi/admin/tokens").with(httpBasic("demo", "demo")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/openapi/admin/tokens").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should answer a bad bearer token with a JSON problem and a Bearer challenge")
    void shouldAnswerUnauthorizedAsProblemJson() throws Exception {
        final MvcResult result = mockMvc.perform(get("/api/demo/payments")
                        .header("Authorization", "Bearer atk_abcd1234_wrongsecret"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":401");
    }
}
