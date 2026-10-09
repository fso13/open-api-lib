package ru.openapi.tokens.sample.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.openapi.tokens.sample.LoginViewController;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.autoconfigure.OpenApiTokensProperties;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.ScopeCatalog;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = {
        LoginViewController.class,
        HomeController.class,
        UserTokenViewController.class,
        ScopeCatalogViewController.class,
        AdminTokenViewController.class
})
@Import({UiSecurityConfig.class, TokenUiMapper.class})
@DisplayName("UI security and entry points")
class UiAccessControlTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(UiTestTokens.NOW, ZoneId.of("UTC"));
        }

        @Bean
        OpenApiTokensProperties openApiTokensProperties() {
            return new OpenApiTokensProperties();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private ScopeCatalog scopeCatalog;

    @Test
    @DisplayName("GET /login is public and renders the login form")
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Вход в консоль")));
    }

    @Test
    @DisplayName("GET / sends regular users to their token list")
    void homeRedirectsUser() throws Exception {
        mockMvc.perform(get("/").with(user("demo").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tokens"));
    }

    @Test
    @DisplayName("GET / sends admins to the admin console")
    void homeRedirectsAdmin() throws Exception {
        mockMvc.perform(get("/").with(user("admin").roles("ADMIN", "USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tokens"));
    }

    @Test
    @DisplayName("GET / requires authentication")
    void homeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("The admin console is reachable for admins only")
    void adminConsoleIsAdminOnly() throws Exception {
        when(apiTokenService.listAll()).thenReturn(List.of(UiTestTokens.active("ci-bot", "demo")));

        mockMvc.perform(get("/admin/tokens").with(user("admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tokens"));

        mockMvc.perform(get("/admin/tokens").with(user("demo").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The scope reference is available to every authenticated user")
    void scopeReferenceIsAvailable() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of());

        mockMvc.perform(get("/ui/scopes").with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/scopes"));
    }

    @Test
    @DisplayName("Admins can still use the user page")
    void adminCanUseUserPage() throws Exception {
        when(apiTokenService.listOwn()).thenReturn(List.of());

        mockMvc.perform(get("/ui/tokens").with(user("admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/tokens"));
    }

    @Test
    @DisplayName("The stylesheet is served without authentication")
    void stylesheetIsPublic() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown pages under the UI are not exposed")
    void unknownUiPageIsNotFound() throws Exception {
        mockMvc.perform(get("/ui/unknown").with(user("demo").roles("USER")))
                .andExpect(status().isNotFound());
    }

}
