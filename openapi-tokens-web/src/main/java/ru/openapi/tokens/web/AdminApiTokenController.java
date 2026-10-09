package ru.openapi.tokens.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.web.dto.ApiTokenResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/openapi/admin/tokens")
public class AdminApiTokenController {

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
    public List<ApiTokenResponse> list() {
        return apiTokenService.listAll().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ApiTokenResponse get(@PathVariable UUID id) {
        return mapper.toResponse(apiTokenService.getAny(id));
    }

    @GetMapping("/{id}/audit")
    public List<?> audit(@PathVariable UUID id) {
        apiTokenService.getAny(id);
        return auditLogRepository.findByTokenId(id, 0, 100);
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
