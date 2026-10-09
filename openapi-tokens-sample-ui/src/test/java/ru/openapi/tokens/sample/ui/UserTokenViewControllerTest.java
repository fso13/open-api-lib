package ru.openapi.tokens.sample.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.exception.ApiTokenNotFoundException;
import ru.openapi.tokens.token.exception.QuotaExceededException;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.ScopeCatalog;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest
@Import(UserTokenViewController.class)
@DisplayName("User token UI")
class UserTokenViewControllerTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenUiMapper tokenUiMapper;

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private ScopeCatalog scopeCatalog;

    @Test
    @DisplayName("GET /ui/tokens redirects anonymous visitors to the login page")
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/ui/tokens"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("GET /ui/tokens renders own tokens")
    void listsOwnTokens() throws Exception {
        when(apiTokenService.listOwn()).thenReturn(List.of(
                UiTestTokens.active("ci-bot", "demo"),
                UiTestTokens.revoked("legacy", "demo")
        ));

        mockMvc.perform(get("/ui/tokens").with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/tokens"))
                .andExpect(model().attribute("tokens", hasSize(2)))
                .andExpect(content().string(containsString("ci-bot")))
                .andExpect(content().string(containsString("Мои API-токены")));
    }

    @Test
    @DisplayName("GET /ui/tokens applies the status filter")
    void filtersByStatus() throws Exception {
        when(apiTokenService.listOwn()).thenReturn(List.of(
                UiTestTokens.active("ci-bot", "demo"),
                UiTestTokens.revoked("legacy", "demo")
        ));

        mockMvc.perform(get("/ui/tokens").param("status", "REVOKED").with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokens", hasSize(1)))
                .andExpect(content().string(containsString("legacy")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">ci-bot<"))));
    }

    @Test
    @DisplayName("POST /ui/tokens creates a token and shows the raw value once")
    void createsToken() throws Exception {
        when(apiTokenService.create(any(CreateTokenCommand.class)))
                .thenReturn(new CreatedApiToken(UiTestTokens.active("ci-bot", "demo"), UiTestTokens.rawToken()));

        mockMvc.perform(post("/ui/tokens")
                        .with(user("demo"))
                        .with(csrf())
                        .param("name", "ci-bot")
                        .param("description", "CI pipeline")
                        .param("scopes", "payments:read, payments:write")
                        .param("expiresAt", "2026-11-01T10:30")
                        .param("slidingTtlSeconds", "3600")
                        .param("rateLimitRequests", "100")
                        .param("rateLimitWindowSeconds", "60")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens/created"))
                .andExpect(flash().attribute("rawToken", "atk_abcd1234_s3cr3tvalue"))
                .andExpect(flash().attributeExists("token"));

        final ArgumentCaptor<CreateTokenCommand> captor = ArgumentCaptor.forClass(CreateTokenCommand.class);
        verify(apiTokenService).create(captor.capture());
        final CreateTokenCommand command = captor.getValue();
        assertThat(command.name()).isEqualTo("ci-bot");
        assertThat(command.description()).isEqualTo("CI pipeline");
        assertThat(command.scopes()).containsExactly("payments:read", "payments:write");
        assertThat(command.expiresAt()).isEqualTo(Instant.parse("2026-11-01T10:30:00Z"));
        assertThat(command.slidingTtl()).hasSeconds(3600);
        assertThat(command.rateLimit().requests()).isEqualTo(100);
        assertThat(command.rateLimit().windowSeconds()).isEqualTo(60);
        assertThat(command.tenantId()).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("POST /ui/tokens re-renders the form when validation fails")
    void rejectsInvalidForm() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of());

        mockMvc.perform(post("/ui/tokens")
                        .with(user("demo"))
                        .with(csrf())
                        .param("name", "")
                        .param("scopes", ",,,")
                        .param("expiresAt", "not-a-date")
                        .param("rateLimitRequests", "100"))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/token-new"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "scopes", "expiresAt",
                        "rateLimitRequests"));
    }

    @Test
    @DisplayName("POST /ui/tokens redirects to the create form when the quota is exceeded")
    void quotaExceededRedirectsToForm() throws Exception {
        when(apiTokenService.create(any(CreateTokenCommand.class)))
                .thenThrow(new QuotaExceededException("demo", 10));

        mockMvc.perform(post("/ui/tokens")
                        .with(user("demo"))
                        .with(csrf())
                        .param("name", "ci-bot")
                        .param("scopes", "payments:read"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens/new"))
                .andExpect(flash().attribute("error", "Достигнут лимит токенов для владельца «demo»: максимум 10"));
    }

    @Test
    @DisplayName("GET /ui/tokens/new renders the scope catalog with descriptions")
    void createFormShowsScopeCatalog() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of(
                new ScopeInfo("payments:read", "Чтение платежей", Set.of()),
                new ScopeInfo("legacy", null, Set.of())
        ));

        mockMvc.perform(get("/ui/tokens/new").with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/token-new"))
                .andExpect(model().attribute("scopes", hasSize(2)))
                .andExpect(content().string(containsString("Чтение платежей")))
                .andExpect(content().string(containsString("описание не задано")))
                .andExpect(content().string(containsString("data-scope=\"payments:read\"")));
    }

    @Test
    @DisplayName("GET /ui/tokens/{id} shows stats and audit trail")
    void showsTokenDetails() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.get(id)).thenReturn(UiTestTokens.active("ci-bot", "demo"));
        when(apiTokenService.usageStats(id)).thenReturn(new TokenUsageStats(id, 10, 9, 1, UiTestTokens.NOW));
        when(auditLogRepository.findByTokenId(id, 0, 50)).thenReturn(List.of(new AuditEvent(
                id, "demo", "tenant-demo", "GET", "/api/demo/payments", "AUTHENTICATE",
                true, 200, "127.0.0.1", "curl/8.7.1", UiTestTokens.NOW
        )));

        mockMvc.perform(get("/ui/tokens/{id}", id).with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/token-detail"))
                .andExpect(model().attributeExists("token", "usage", "audit"))
                .andExpect(content().string(containsString("Журнал аудита")))
                .andExpect(content().string(containsString("AUTHENTICATE")));
    }

    @Test
    @DisplayName("GET /ui/tokens/{id} of a foreign token redirects with a flash error")
    void foreignTokenIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.get(id)).thenThrow(new ApiTokenNotFoundException(id));

        mockMvc.perform(get("/ui/tokens/{id}", id).with(user("demo")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens"))
                .andExpect(flash().attribute("error", "Токен не найден или недоступен"));
    }

    @Test
    @DisplayName("POST /ui/tokens/{id}/revoke revokes and returns to the detail page")
    void revokesToken() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.revoke(any(RevokeTokenCommand.class)))
                .thenReturn(UiTestTokens.revoked("ci-bot", "demo"));

        mockMvc.perform(post("/ui/tokens/{id}/revoke", id).with(user("demo")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens/" + id))
                .andExpect(flash().attribute("success", "Токен отозван"));

        final ArgumentCaptor<RevokeTokenCommand> captor = ArgumentCaptor.forClass(RevokeTokenCommand.class);
        verify(apiTokenService).revoke(captor.capture());
        assertThat(captor.getValue().tokenId()).isEqualTo(id);
    }

    @Test
    @DisplayName("GET /ui/tokens/created shows the raw token from the flash attribute")
    void createdPageShowsRawToken() throws Exception {
        mockMvc.perform(get("/ui/tokens/created")
                        .with(user("demo"))
                        .flashAttr("rawToken", "atk_abcd1234_s3cr3tvalue")
                        .flashAttr("token", tokenUiMapper.toView(UiTestTokens.active("ci-bot", "demo"))))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/token-created"))
                .andExpect(content().string(containsString("atk_abcd1234_s3cr3tvalue")))
                .andExpect(content().string(containsString("Скопируйте токен сейчас")));
    }

    @Test
    @DisplayName("POST /ui/tokens requires a CSRF token")
    void rejectsMissingCsrf() throws Exception {
        mockMvc.perform(post("/ui/tokens")
                        .with(user("demo"))
                        .param("name", "ci-bot")
                        .param("scopes", "payments:read"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /ui/tokens/created without a fresh token redirects to the list")
    void createdPageWithoutFlashRedirects() throws Exception {
        mockMvc.perform(get("/ui/tokens/created").with(user("demo")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens"));
    }

    @Test
    @DisplayName("POST /ui/tokens rejects a partially filled rate limit")
    void rejectsPartialRateLimit() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of());

        mockMvc.perform(post("/ui/tokens")
                        .with(user("demo"))
                        .with(csrf())
                        .param("name", "ci-bot")
                        .param("scopes", "payments:read")
                        .param("rateLimitWindowSeconds", "60"))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/token-new"))
                .andExpect(model().attributeHasFieldErrors("form", "rateLimitRequests"));
    }

    @Test
    @DisplayName("POST /ui/tokens works without optional fields")
    void createsMinimalToken() throws Exception {
        when(apiTokenService.create(any(CreateTokenCommand.class)))
                .thenReturn(new CreatedApiToken(UiTestTokens.active("ci-bot", "demo"), UiTestTokens.rawToken()));

        mockMvc.perform(post("/ui/tokens")
                        .with(user("demo"))
                        .with(csrf())
                        .param("name", "ci-bot")
                        .param("scopes", "payments:read"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens/created"));

        final ArgumentCaptor<CreateTokenCommand> captor = ArgumentCaptor.forClass(CreateTokenCommand.class);
        verify(apiTokenService).create(captor.capture());
        assertThat(captor.getValue().expiresAt()).isNull();
        assertThat(captor.getValue().slidingTtl()).isNull();
        assertThat(captor.getValue().rateLimit()).isNull();
        assertThat(captor.getValue().tenantId()).isNull();
    }

    @Test
    @DisplayName("GET /ui/tokens/{id} of a missing token goes through the UI advice, not the REST one")
    void missingTokenIsHandledByUiAdvice() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.get(eq(id))).thenThrow(new ApiTokenNotFoundException(id));

        mockMvc.perform(get("/ui/tokens/{id}", id).with(user("demo")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }
}
