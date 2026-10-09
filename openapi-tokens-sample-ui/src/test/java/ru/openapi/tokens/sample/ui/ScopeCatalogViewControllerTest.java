package ru.openapi.tokens.sample.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.spi.ScopeCatalog;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest
@Import(ScopeCatalogViewController.class)
@DisplayName("Scope catalog page")
class ScopeCatalogViewControllerTest {


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScopeCatalog scopeCatalog;

    @Test
    @DisplayName("GET /ui/scopes renders descriptions and projected authorities")
    void rendersCatalog() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of(
                new ScopeInfo("payments:read", "Чтение платежей", Set.of("readPayment", "listPayments")),
                new ScopeInfo("legacy", null, Set.of())
        ));

        mockMvc.perform(get("/ui/scopes").with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/scopes"))
                .andExpect(model().attribute("scopes", hasSize(2)))
                .andExpect(model().attribute("scopesMode", "identity"))
                .andExpect(content().string(containsString("Чтение платежей")))
                .andExpect(content().string(containsString("readPayment")))
                .andExpect(content().string(containsString("режим: identity")))
                .andExpect(content().string(containsString("payments:read")));
    }

    @Test
    @DisplayName("GET /ui/scopes renders a hint when the catalog is empty")
    void rendersEmptyCatalog() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of());

        mockMvc.perform(get("/ui/scopes").with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("scopes", hasSize(0)))
                .andExpect(content().string(containsString("Справочник пуст")))
                .andExpect(content().string(not(containsString("readPayment"))));
    }

    @Test
    @DisplayName("GET /ui/scopes requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/ui/scopes"))
                .andExpect(status().is3xxRedirection());
    }
}
