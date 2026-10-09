package ru.openapi.tokens.sample.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The UI filter chain must own {@code /css/**} and {@code /error}: otherwise the stylesheet falls
 * through to another chain (the starter's or the application's API chain) and the pages render
 * unstyled — or the request is answered with 401.
 */
@WebMvcTest
@DisplayName("UI static resources and public paths")
class UiStaticResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /css/app.css is served without authentication")
    void stylesheetIsPublic() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--accent")));
    }

    @Test
    @DisplayName("GET /ui/** is still protected")
    void uiIsProtected() throws Exception {
        mockMvc.perform(get("/ui/tokens"))
                .andExpect(status().is3xxRedirection());
    }
}
