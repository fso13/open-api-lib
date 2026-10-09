package ru.openapi.tokens.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.web.dto.ScopeInfoResponse;

import java.util.List;

/**
 * Read-only scope catalog: every known token scope with its description and the project
 * authorities it grants.
 */
@RestController
@RequestMapping("/api/openapi/scopes")
public class ScopeCatalogController {

    private final ScopeCatalog scopeCatalog;
    private final ApiTokenWebMapper mapper;

    public ScopeCatalogController(ScopeCatalog scopeCatalog, ApiTokenWebMapper mapper) {
        this.scopeCatalog = scopeCatalog;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ScopeInfoResponse> list() {
        return mapper.toScopeResponses(scopeCatalog.listScopes());
    }
}
