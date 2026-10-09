package ru.openapi.tokens.sample.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.openapi.tokens.autoconfigure.OpenApiTokensProperties;
import ru.openapi.tokens.token.spi.ScopeCatalog;

/**
 * Read-only reference of every scope known to the starter: description plus the authorities the
 * scope grants. Available to every authenticated user.
 */
@Controller
@RequestMapping("/ui/scopes")
public class ScopeCatalogViewController {

    private final ScopeCatalog scopeCatalog;
    private final TokenUiMapper mapper;
    private final OpenApiTokensProperties properties;

    public ScopeCatalogViewController(
            ScopeCatalog scopeCatalog,
            TokenUiMapper mapper,
            OpenApiTokensProperties properties
    ) {
        this.scopeCatalog = scopeCatalog;
        this.mapper = mapper;
        this.properties = properties;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scopes", mapper.toScopeViews(scopeCatalog.listScopes()));
        model.addAttribute("scopesMode", properties.getScopes().getMode());
        return "ui/scopes";
    }
}
