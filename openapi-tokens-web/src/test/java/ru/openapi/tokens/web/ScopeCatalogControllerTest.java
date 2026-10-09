package ru.openapi.tokens.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.spi.ScopeCatalog;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeCatalogController")
class ScopeCatalogControllerTest {

    @Mock
    private ScopeCatalog scopeCatalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScopeCatalogController(scopeCatalog, new ApiTokenWebMapper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("Should list scopes with descriptions and projected authorities")
    void shouldListScopes() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of(
                new ScopeInfo("payments:read", "Чтение платежей", Set.of("readPayment")),
                new ScopeInfo("legacy", null, Set.of())
        ));

        mockMvc.perform(get("/api/openapi/scopes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].scope").value("payments:read"))
                .andExpect(jsonPath("$[0].description").value("Чтение платежей"))
                .andExpect(jsonPath("$[0].projectScopes", hasSize(1)))
                .andExpect(jsonPath("$[0].projectScopes[0]").value("readPayment"))
                .andExpect(jsonPath("$[1].scope").value("legacy"))
                .andExpect(jsonPath("$[1].description").value(nullValue()))
                .andExpect(jsonPath("$[1].projectScopes", hasSize(0)));
    }

    @Test
    @DisplayName("Should return an empty array when no scope is declared")
    void shouldReturnEmptyArray() throws Exception {
        when(scopeCatalog.listScopes()).thenReturn(List.of());

        mockMvc.perform(get("/api/openapi/scopes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
