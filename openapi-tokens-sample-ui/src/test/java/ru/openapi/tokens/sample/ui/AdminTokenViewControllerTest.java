package ru.openapi.tokens.sample.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
@Import(AdminTokenViewController.class)
@DisplayName("Admin token UI")
class AdminTokenViewControllerTest {

    private static final String ADMIN = "admin";


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("GET /admin/tokens lists tokens of every owner")
    void listsAllTokens() throws Exception {
        when(apiTokenService.listAll()).thenReturn(List.of(
                UiTestTokens.active("ci-bot", "demo"),
                UiTestTokens.blocked("legacy", "other")
        ));

        mockMvc.perform(get("/admin/tokens").with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tokens"))
                .andExpect(model().attribute("tokens", hasSize(2)))
                .andExpect(model().attribute("counters", org.hamcrest.Matchers.notNullValue()))
                .andExpect(content().string(containsString("Владелец")))
                .andExpect(content().string(containsString("ci-bot")))
                .andExpect(content().string(containsString("legacy")));
    }

    @Test
    @DisplayName("GET /admin/tokens filters by owner, text and status")
    void filtersTokens() throws Exception {
        when(apiTokenService.listAll()).thenReturn(List.of(
                UiTestTokens.active("ci-bot", "demo"),
                UiTestTokens.blocked("legacy", "other")
        ));

        mockMvc.perform(get("/admin/tokens")
                        .param("owner", "other")
                        .with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokens", hasSize(1)))
                .andExpect(content().string(containsString("legacy")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">ci-bot<"))));

        mockMvc.perform(get("/admin/tokens")
                        .param("q", "ci")
                        .param("status", "ACTIVE")
                        .with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokens", hasSize(1)))
                .andExpect(content().string(containsString("ci-bot")));
    }

    @Test
    @DisplayName("GET /admin/tokens/{id} shows a foreign token with usage stats and audit trail")
    void showsAnyTokenDetails() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.getAny(id)).thenReturn(UiTestTokens.active("ci-bot", "demo"));
        when(auditLogRepository.usageStats(id)).thenReturn(new TokenUsageStats(id, 4, 3, 1, UiTestTokens.NOW));
        when(auditLogRepository.findByTokenId(id, 0, 100)).thenReturn(List.of());

        mockMvc.perform(get("/admin/tokens/{id}", id).with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/token-detail"))
                .andExpect(model().attributeExists("token", "usage", "audit"))
                .andExpect(content().string(containsString("Владелец")))
                .andExpect(content().string(containsString("Заблокировать")));
    }

    @Test
    @DisplayName("POST /admin/tokens/{id}/block blocks via the admin API")
    void blocksToken() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.forceBlock(any(BlockTokenCommand.class)))
                .thenReturn(UiTestTokens.blocked("ci-bot", "demo"));

        mockMvc.perform(post("/admin/tokens/{id}/block", id).with(user(ADMIN).roles("ADMIN", "USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tokens/" + id))
                .andExpect(flash().attribute("success", "Токен заблокирован"));

        final ArgumentCaptor<BlockTokenCommand> captor = ArgumentCaptor.forClass(BlockTokenCommand.class);
        verify(apiTokenService).forceBlock(captor.capture());
        assertThat(captor.getValue().block()).isTrue();
        assertThat(captor.getValue().tokenId()).isEqualTo(id);
    }

    @Test
    @DisplayName("POST /admin/tokens/{id}/unblock unblocks the token")
    void unblocksToken() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.forceBlock(any(BlockTokenCommand.class)))
                .thenReturn(UiTestTokens.active("ci-bot", "demo"));

        mockMvc.perform(post("/admin/tokens/{id}/unblock", id).with(user(ADMIN).roles("ADMIN", "USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Токен разблокирован"));

        final ArgumentCaptor<BlockTokenCommand> captor = ArgumentCaptor.forClass(BlockTokenCommand.class);
        verify(apiTokenService).forceBlock(captor.capture());
        assertThat(captor.getValue().block()).isFalse();
    }

    @Test
    @DisplayName("POST /admin/tokens/{id}/revoke force revokes the token")
    void forceRevokesToken() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.forceRevoke(any(RevokeTokenCommand.class)))
                .thenReturn(UiTestTokens.revoked("ci-bot", "demo"));

        mockMvc.perform(post("/admin/tokens/{id}/revoke", id).with(user(ADMIN).roles("ADMIN", "USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Токен отозван администратором"));

        verify(apiTokenService).forceRevoke(any(RevokeTokenCommand.class));
    }

    @Test
    @DisplayName("POST /admin/tokens/{id}/revoke on an invalid state redirects with a flash error")
    void invalidStateRedirectsWithError() throws Exception {
        final UUID id = UUID.randomUUID();
        when(apiTokenService.forceRevoke(any(RevokeTokenCommand.class)))
                .thenThrow(new InvalidTokenStateException("Token already revoked", TokenStatus.REVOKED));

        mockMvc.perform(post("/admin/tokens/{id}/revoke", id).with(user(ADMIN).roles("ADMIN", "USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tokens"))
                .andExpect(flash().attribute("error", "Действие невозможно в текущем состоянии токена"));
    }

    @Test
    @DisplayName("GET /admin/tokens is forbidden for a plain user")
    void forbiddenForPlainUser() throws Exception {
        mockMvc.perform(get("/admin/tokens").with(user("demo").roles("USER")))
                .andExpect(status().isForbidden());

        verify(apiTokenService, never()).listAll();
    }

    @Test
    @DisplayName("GET /admin/tokens redirects anonymous visitors to the login page")
    void anonymousIsRedirected() throws Exception {
        mockMvc.perform(get("/admin/tokens"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("Admin actions reject requests without a CSRF token")
    void blockRequiresCsrf() throws Exception {
        final UUID id = UUID.randomUUID();

        mockMvc.perform(post("/admin/tokens/{id}/block", id).with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isForbidden());

        verify(apiTokenService, never()).forceBlock(any(BlockTokenCommand.class));
    }

    @Test
    @DisplayName("Filtering keeps the list empty when nothing matches")
    void emptyResultIsRendered() throws Exception {
        when(apiTokenService.listAll()).thenReturn(List.of(UiTestTokens.active("ci-bot", "demo")));

        mockMvc.perform(get("/admin/tokens").param("owner", "nobody")
                        .with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokens", hasSize(0)))
                .andExpect(content().string(containsString("Токены не найдены")));
    }

    @Test
    @DisplayName("Counters are derived from every token, not only the filtered ones")
    void countersUseAllTokens() throws Exception {
        final List<ApiToken> all = List.of(
                UiTestTokens.active("ci-bot", "demo"),
                UiTestTokens.blocked("legacy", "other"),
                UiTestTokens.expired("old", "other")
        );
        when(apiTokenService.listAll()).thenReturn(all);

        mockMvc.perform(get("/admin/tokens").param("status", "BLOCKED")
                        .with(user(ADMIN).roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokens", hasSize(1)))
                .andExpect(model().attribute("counters", TokenCounters.of(all)));
    }
}
