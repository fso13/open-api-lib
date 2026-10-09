package ru.openapi.tokens.sample.ui;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form backing bean for the "create token" page.
 *
 * <p>Scopes are entered as a free text list (comma / space / newline separated) because in
 * {@code identity} scope mode any authority name is valid. {@code expiresAt} is the raw value of a
 * {@code <input type="datetime-local">} element and is parsed explicitly by {@link TokenUiMapper}.</p>
 */
public record CreateTokenForm(
        @NotBlank(message = "Укажите название токена")
        @Size(max = 128, message = "Название не длиннее 128 символов")
        String name,

        @Size(max = 512, message = "Описание не длиннее 512 символов")
        String description,

        @NotBlank(message = "Укажите хотя бы один scope")
        String scopes,

        String expiresAt,

        @Min(value = 30, message = "Sliding TTL — минимум 30 секунд")
        Integer slidingTtlSeconds,

        @Min(value = 1, message = "Лимит запросов — минимум 1")
        Integer rateLimitRequests,

        @Min(value = 1, message = "Окно лимита — минимум 1 секунда")
        Integer rateLimitWindowSeconds,

        @Size(max = 64, message = "tenantId не длиннее 64 символов")
        String tenantId
) {

    public static CreateTokenForm empty() {
        return new CreateTokenForm(null, null, null, null, null, null, null, null);
    }
}
