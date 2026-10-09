package ru.openapi.tokens.sample.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import ru.openapi.tokens.token.exception.ApiTokenNotFoundException;
import ru.openapi.tokens.token.exception.EmptyScopesException;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;
import ru.openapi.tokens.token.exception.QuotaExceededException;

/**
 * Turns domain exceptions raised while rendering UI pages into friendly redirects with a Russian
 * flash message instead of the JSON {@code ProblemDetail} produced by the REST advice.
 *
 * <p>Ordered before {@code ApiTokenExceptionHandler} and limited to the UI controllers, so the REST
 * API keeps returning RFC 7807 payloads.</p>
 */
@ControllerAdvice(basePackages = "ru.openapi.tokens.sample.ui")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UiExceptionAdvice {

    @ExceptionHandler(ApiTokenNotFoundException.class)
    public String handleNotFound(ApiTokenNotFoundException ex, HttpServletRequest request) {
        return redirectWithError(request, listPath(request), "Токен не найден или недоступен");
    }

    @ExceptionHandler(QuotaExceededException.class)
    public String handleQuota(QuotaExceededException ex, HttpServletRequest request) {
        return redirectWithError(request, "/ui/tokens/new",
                "Достигнут лимит токенов для владельца «%s»: максимум %d"
                        .formatted(ex.getOwnerId(), ex.getMaxTokens()));
    }

    @ExceptionHandler(EmptyScopesException.class)
    public String handleEmptyScopes(EmptyScopesException ex, HttpServletRequest request) {
        return redirectWithError(request, "/ui/tokens/new", "Укажите хотя бы один scope");
    }

    @ExceptionHandler(InvalidTokenStateException.class)
    public String handleInvalidState(InvalidTokenStateException ex, HttpServletRequest request) {
        return redirectWithError(request, listPath(request),
                "Действие невозможно в текущем состоянии токена");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return redirectWithError(request, listPath(request), "Некорректный запрос: " + ex.getMessage());
    }

    private static String listPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/admin") ? "/admin/tokens" : "/ui/tokens";
    }

    private static String redirectWithError(HttpServletRequest request, String target, String message) {
        final FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        if (flashMap != null) {
            flashMap.put("error", message);
        }
        return "redirect:" + target;
    }
}
