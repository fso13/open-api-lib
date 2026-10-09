package ru.openapi.tokens.sample.ui;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;

import java.util.List;
import java.util.UUID;

/**
 * Admin UI: every token of every owner, with search/filters, block, unblock and force revoke.
 *
 * <p>URL level protection lives in {@link UiSecurityConfig} ({@code /admin/**} requires
 * {@code ROLE_ADMIN}); the {@link PreAuthorize} annotation is defence in depth.</p>
 */
@Controller
@RequestMapping("/admin/tokens")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTokenViewController {

    /** Audit entries shown on the admin detail page. */
    private static final int AUDIT_PAGE_SIZE = 100;

    private static final String LIST_PATH = "/admin/tokens";

    private final ApiTokenService apiTokenService;
    private final AuditLogRepository auditLogRepository;
    private final TokenUiMapper mapper;

    public AdminTokenViewController(
            ApiTokenService apiTokenService,
            AuditLogRepository auditLogRepository,
            TokenUiMapper mapper
    ) {
        this.apiTokenService = apiTokenService;
        this.auditLogRepository = auditLogRepository;
        this.mapper = mapper;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String status,
            Model model
    ) {
        final List<ApiToken> all = apiTokenService.listAll();
        final TokenFilter filter = TokenFilter.of(q, owner, status);
        model.addAttribute("tokens", mapper.toViews(filter.apply(all)));
        model.addAttribute("filter", filter);
        model.addAttribute("counters", TokenCounters.of(all));
        model.addAttribute("statusOptions", TokenUiMapper.statusOptions());
        model.addAttribute("owners", all.stream().map(ApiToken::ownerId).distinct().sorted().toList());
        return "admin/tokens";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        final ApiToken token = apiTokenService.getAny(id);
        model.addAttribute("token", mapper.toView(token));
        model.addAttribute("usage", mapper.toUsage(auditLogRepository.usageStats(id)));
        model.addAttribute("audit", mapper.toAuditViews(auditLogRepository.findByTokenId(id, 0, AUDIT_PAGE_SIZE)));
        model.addAttribute("ownerVisible", true);
        model.addAttribute("listPath", LIST_PATH);
        return "admin/token-detail";
    }

    @PostMapping("/{id}/block")
    public String block(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        apiTokenService.forceBlock(BlockTokenCommand.block(id));
        redirectAttributes.addFlashAttribute("success", "Токен заблокирован");
        return detailRedirect(id);
    }

    @PostMapping("/{id}/unblock")
    public String unblock(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        apiTokenService.forceBlock(BlockTokenCommand.unblock(id));
        redirectAttributes.addFlashAttribute("success", "Токен разблокирован");
        return detailRedirect(id);
    }

    @PostMapping("/{id}/revoke")
    public String forceRevoke(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        apiTokenService.forceRevoke(new RevokeTokenCommand(id));
        redirectAttributes.addFlashAttribute("success", "Токен отозван администратором");
        return detailRedirect(id);
    }

    private static String detailRedirect(UUID id) {
        return "redirect:" + LIST_PATH + "/" + id;
    }
}
