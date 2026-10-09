package ru.openapi.tokens.web.dto;

public record CreatedTokenResponse(
        ApiTokenResponse token,
        String rawToken
) {
}
