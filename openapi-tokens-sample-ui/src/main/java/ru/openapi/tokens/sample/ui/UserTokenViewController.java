package ru.openapi.tokens.sample.ui;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.AuditLogRepository;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * User facing UI: list, create, inspect and revoke <em>own</em> API tokens.
 * Ownership is enforced by {@link ApiTokenService} itself.
 */
@Controller
@RequestMapping("/ui/tokens")
public class UserTokenViewController {

    /** Audit entries shown on the detail page. */
    private static final int AUDIT_PAGE_SIZE = 50;

    private final ApiTokenService apiTokenService;
    private final AuditLogRepository auditLogRepository;
    private final ScopeCatalog scopeCatalog;
    private final TokenUiMapper mapper;

    public UserTokenViewController(
            ApiTokenService apiTokenService,
            AuditLogRepository auditLogRepository,
            ScopeCatalog scopeCatalog,
            TokenUiMapper mapper
    ) {
        this.apiTokenService = apiTokenService;
        this.auditLogRepository = auditLogRepository;
        this.scopeCatalog = scopeCatalog;
        this.mapper = mapper;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            Model model
    ) {
        final List<ApiToken> own = apiTokenService.listOwn();
        final TokenFilter filter = TokenFilter.of(q, null, status);
        model.addAttribute("tokens", mapper.toViews(filter.apply(own)));
        model.addAttribute("filter", filter);
        model.addAttribute("counters", TokenCounters.of(own));
        model.addAttribute("statusOptions", TokenUiMapper.statusOptions());
        return "ui/tokens";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        return createFormView(CreateTokenForm.empty(), model);
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") CreateTokenForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        validateForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            return createFormView(form, model);
        }
        final CreatedApiToken created = apiTokenService.create(mapper.toCommand(form));
        redirectAttributes.addFlashAttribute("token", mapper.toView(created.token()));
        redirectAttributes.addFlashAttribute("rawToken", created.rawToken().value());
        return "redirect:/ui/tokens/created";
    }

    /**
     * Shows the raw token exactly once, right after creation (value comes from a flash attribute).
     */
    @GetMapping("/created")
    public String created(Model model) {
        if (!model.containsAttribute("rawToken")) {
            return "redirect:/ui/tokens";
        }
        model.addAttribute("baseUrl", ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
        return "ui/token-created";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        final ApiToken token = apiTokenService.get(id);
        model.addAttribute("token", mapper.toView(token));
        model.addAttribute("usage", mapper.toUsage(apiTokenService.usageStats(id)));
        model.addAttribute("audit", mapper.toAuditViews(auditLogRepository.findByTokenId(id, 0, AUDIT_PAGE_SIZE)));
        model.addAttribute("ownerVisible", false);
        model.addAttribute("listPath", "/ui/tokens");
        return "ui/token-detail";
    }

    @PostMapping("/{id}/revoke")
    public String revoke(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        apiTokenService.revoke(new RevokeTokenCommand(id));
        redirectAttributes.addFlashAttribute("success", "Токен отозван");
        return "redirect:/ui/tokens/" + id;
    }

    /**
     * Справочник скоупов берётся из {@link ScopeCatalog} (конфиг + таблица scope_catalog),
     * а не из захардкоженного списка.
     */
    private String createFormView(CreateTokenForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("scopes", mapper.toScopeViews(scopeCatalog.listScopes()));
        return "ui/token-new";
    }

    /**
     * Cross-field checks that bean validation cannot express.
     */
    private void validateForm(CreateTokenForm form, BindingResult bindingResult) {
        if (form.scopes() != null && !form.scopes().isBlank() && TokenUiMapper.parseScopes(form.scopes()).isEmpty()) {
            bindingResult.rejectValue("scopes", "scopes.empty", "Укажите хотя бы один scope");
        }
        try {
            mapper.parseExpiry(form.expiresAt());
        } catch (DateTimeParseException ex) {
            bindingResult.rejectValue("expiresAt", "expiresAt.invalid", "Неверная дата и время");
        }
        if ((form.rateLimitRequests() == null) != (form.rateLimitWindowSeconds() == null)) {
            bindingResult.rejectValue(
                    "rateLimitRequests",
                    "rateLimit.incomplete",
                    "Заполните оба поля лимита или оставьте оба пустыми"
            );
        }
    }
}
