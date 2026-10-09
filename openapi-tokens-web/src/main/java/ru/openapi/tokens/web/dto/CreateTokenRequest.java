package ru.openapi.tokens.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record CreateTokenRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotEmpty Set<@NotBlank String> scopes,
        Instant expiresAt,
        Integer slidingTtlSeconds,
        Integer rateLimitRequests,
        Integer rateLimitWindowSeconds,
        String tenantId
) {
}
