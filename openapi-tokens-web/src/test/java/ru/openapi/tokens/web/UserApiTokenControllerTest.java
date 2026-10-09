package ru.openapi.tokens.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.web.dto.CreateTokenRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserApiTokenController")
class UserApiTokenControllerTest {

    @Mock
    private ApiTokenService apiTokenService;
    @Mock
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserApiTokenController(apiTokenService, new ApiTokenWebMapper(), auditLogRepository))
                .setControllerAdvice(new ApiTokenExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("Should create token and return raw once")
    void shouldCreate() throws Exception {
        final UUID id = UUID.randomUUID();
        final ApiToken token = sample(id);
        final RawToken raw = RawToken.parse("atk_abcd1234_supersecretvalue");
        when(apiTokenService.create(any())).thenReturn(new CreatedApiToken(token, raw));

        mockMvc.perform(post("/api/openapi/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTokenRequest("ci", null, Set.of("read"), null, null, null, null, null)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawToken").value(raw.value()))
                .andExpect(jsonPath("$.token.id").value(id.toString()))
                .andExpect(jsonPath("$.token.prefix").value("abcd1234"));
    }

    @Test
    @DisplayName("Should list own tokens")
    void shouldList() throws Exception {
        when(apiTokenService.listOwn()).thenReturn(List.of(sample(UUID.randomUUID())));

        mockMvc.perform(get("/api/openapi/tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ci"));
    }

    @Test
    @DisplayName("Should revoke token")
    void shouldRevoke() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.revoke(any())).thenReturn(sample(id));

        mockMvc.perform(delete("/api/openapi/tokens/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should return usage stats")
    void shouldReturnStats() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.usageStats(id))
                .thenReturn(new TokenUsageStats(id, 10, 8, 2, Instant.parse("2026-10-07T12:00:00Z")));

        mockMvc.perform(get("/api/openapi/tokens/{id}/stats", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(10));
    }

    private static ApiToken sample(UUID id) {
        return ApiToken.builder()
                .id(id)
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash"))
                .ownerId("owner-1")
                .scopes(Set.of("read"))
                .createdAt(Instant.parse("2026-10-07T10:00:00Z"))
                .build();
    }
}
