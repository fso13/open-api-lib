package ru.openapi.tokens.sample.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.openapi.tokens.sample.ui.AdminTokenViewController;
import ru.openapi.tokens.sample.ui.HomeController;
import ru.openapi.tokens.sample.ui.ScopeCatalogViewController;
import ru.openapi.tokens.sample.ui.UiExceptionAdvice;
import ru.openapi.tokens.sample.ui.UiModelAdvice;
import ru.openapi.tokens.sample.ui.UserTokenViewController;
import ru.openapi.tokens.token.spi.ApiTokenRepository;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The shared token UI behind Keycloak login: unauthenticated visitors are sent to Keycloak, roles
 * come from realm roles.
 */
@WebMvcTest(controllers = {
        UserTokenViewController.class,
        AdminTokenViewController.class,
        ScopeCatalogViewController.class,
        HomeController.class
})
@Import({
        KeycloakSecurityConfig.class,
        KeycloakTestConfig.class,
        UiModelAdvice.class,
        UiExceptionAdvice.class
})
@DisplayName("Keycloak-secured token UI")
class KeycloakUiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @BeforeEach
    void setUp() {
        when(apiTokenRepository.findByOwnerId(anyString())).thenReturn(java.util.List.of());
        when(apiTokenRepository.findAll()).thenReturn(java.util.List.of());
    }

    @Test
    @DisplayName("GET /ui/tokens sends anonymous visitors to Keycloak login")
    void anonymousIsSentToKeycloak() throws Exception {
        mockMvc.perform(get("/ui/tokens"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/keycloak"));
    }

    @Test
    @DisplayName("GET /ui/tokens renders the shared UI for a logged in Keycloak user")
    void rendersUserPageForKeycloakUser() throws Exception {
        mockMvc.perform(get("/ui/tokens")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1").claim("tenant_id", "tenant-a"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/tokens"))
                .andExpect(content().string(containsString("Мои API-токены")));
    }

    @Test
    @DisplayName("GET /admin/tokens requires the ADMIN realm role")
    void adminConsoleRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/admin/tokens")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/tokens")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tokens"));
    }

    @Test
    @DisplayName("GET /ui/scopes is available to every logged in user")
    void scopeReferenceIsAvailable() throws Exception {
        mockMvc.perform(get("/ui/scopes")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/scopes"));
    }

    @Test
    @DisplayName("GET / routes by realm role")
    void homeRoutesByRole() throws Exception {
        mockMvc.perform(get("/")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(redirectedUrl("/admin/tokens"));

        mockMvc.perform(get("/")
                        .with(jwt().jwt(jwt -> jwt.subject("kc-user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(redirectedUrl("/ui/tokens"));
    }
}
