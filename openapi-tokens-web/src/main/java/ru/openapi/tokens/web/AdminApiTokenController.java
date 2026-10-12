package ru.openapi.tokens.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.web.dto.ApiTokenResponse;
import ru.openapi.tokens.web.dto.AuditEventResponse;

import java.util.List;
import java.util.UUID;

/**
 * Admin API. Protected by the starter's security chain ({@code /api/openapi/admin/**} →
 * {@code ROLE_ADMIN}) and additionally by method security as defence in depth: the annotation is
 * ignored when the application does not enable method security, and enforced when it does.
 */
@RestController
@RequestMapping("/api/openapi/admin/tokens")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiTokenController {

    private static final int MAX_AUDIT_PAGE_SIZE = 500;

    /** Page size limit for the token list as well. */
    private static final int MAX_PAGE_SIZE = 500;

    /** Total number of tokens, exposed for paging UI. */
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final ApiTokenService apiTokenService;
    private final ApiTokenWebMapper mapper;
    private final AuditLogRepository auditLogRepository;

    public AdminApiTokenController(
            ApiTokenService apiTokenService,
            ApiTokenWebMapper mapper,
            AuditLogRepository auditLogRepository
    ) {
        this.apiTokenService = apiTokenService;
        this.mapper = mapper;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<ApiTokenResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(MAX_PAGE_SIZE) int limit
    ) {
        final List<ApiTokenResponse> items = apiTokenService.listAll(offset, limit).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(apiTokenService.countAll()))
                .body(items);
    }

    @GetMapping("/{id}")
    public ApiTokenResponse get(@PathVariable UUID id) {
        return mapper.toResponse(apiTokenService.getAny(id));
    }

    @GetMapping("/{id}/audit")
    public List<AuditEventResponse> audit(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(MAX_AUDIT_PAGE_SIZE) int limit
    ) {
        apiTokenService.getAny(id);
        return mapper.toAuditResponses(auditLogRepository.findByTokenId(id, offset, limit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> forceRevoke(@PathVariable UUID id) {
        apiTokenService.forceRevoke(new RevokeTokenCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/block")
    public ApiTokenResponse block(@PathVariable UUID id) {
        return mapper.toResponse(apiTokenService.forceBlock(BlockTokenCommand.block(id)));
    }

    @PostMapping("/{id}/unblock")
    public ApiTokenResponse unblock(@PathVariable UUID id) {
        return mapper.toResponse(apiTokenService.forceBlock(BlockTokenCommand.unblock(id)));
    }
}
