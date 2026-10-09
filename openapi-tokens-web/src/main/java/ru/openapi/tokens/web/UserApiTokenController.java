package ru.openapi.tokens.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.web.dto.ApiTokenResponse;
import ru.openapi.tokens.web.dto.CreateTokenRequest;
import ru.openapi.tokens.web.dto.CreatedTokenResponse;
import ru.openapi.tokens.web.dto.TokenUsageStatsResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/openapi/tokens")
public class UserApiTokenController {

    private final ApiTokenService apiTokenService;
    private final ApiTokenWebMapper mapper;
    private final AuditLogRepository auditLogRepository;

    public UserApiTokenController(
            ApiTokenService apiTokenService,
            ApiTokenWebMapper mapper,
            AuditLogRepository auditLogRepository
    ) {
        this.apiTokenService = apiTokenService;
        this.mapper = mapper;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping
    public ResponseEntity<CreatedTokenResponse> create(@Valid @RequestBody CreateTokenRequest request) {
        final var created = apiTokenService.create(mapper.toCommand(request));
        final var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.token().id())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toCreatedResponse(created));
    }

    @GetMapping
    public List<ApiTokenResponse> list() {
        return apiTokenService.listOwn().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ApiTokenResponse get(@PathVariable UUID id) {
        return mapper.toResponse(apiTokenService.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        apiTokenService.revoke(new RevokeTokenCommand(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public TokenUsageStatsResponse stats(@PathVariable UUID id) {
        return mapper.toStatsResponse(apiTokenService.usageStats(id));
    }

    @GetMapping("/{id}/audit")
    public List<?> audit(@PathVariable UUID id) {
        apiTokenService.get(id);
        return auditLogRepository.findByTokenId(id, 0, 50);
    }
}
