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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Sample application integration")
class SampleApplicationIT {

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
    @DisplayName("Should create token as user and call demo API with bearer token")
    void shouldCreateTokenAndCallDemoApi() throws Exception {
        final MvcResult created = mockMvc.perform(post("/api/openapi/tokens")
                        .with(httpBasic("demo", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ci","scopes":["payments:read"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawToken").exists())
                .andReturn();

        final JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        final String rawToken = body.get("rawToken").asText();
        assertThat(rawToken).startsWith("atk_");

        mockMvc.perform(get("/api/demo/payments")
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("granted"));
    }
}
