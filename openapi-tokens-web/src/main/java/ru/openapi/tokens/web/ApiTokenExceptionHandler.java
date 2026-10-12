package ru.openapi.tokens.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.openapi.tokens.token.exception.ApiTokenNotFoundException;
import ru.openapi.tokens.token.exception.ConcurrentTokenModificationException;
import ru.openapi.tokens.token.exception.EmptyScopesException;
import ru.openapi.tokens.token.exception.InvalidScopeException;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;
import ru.openapi.tokens.token.exception.QuotaExceededException;
import ru.openapi.tokens.token.exception.TenantNotAllowedException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps token domain errors of the {@code ru.openapi.tokens.web} controllers to RFC 7807 responses.
 *
 * <p>The advice is intentionally limited to this package so that it does not hijack exception
 * handling of the host application's controllers.</p>
 */
@RestControllerAdvice(basePackages = "ru.openapi.tokens.web")
public class ApiTokenExceptionHandler {

    @ExceptionHandler(ApiTokenNotFoundException.class)
    public ProblemDetail handleNotFound(ApiTokenNotFoundException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("API Token Not Found");
        return pd;
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuota(QuotaExceededException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Token Quota Exceeded");
        return pd;
    }

    @ExceptionHandler(InvalidScopeException.class)
    public ProblemDetail handleInvalidScope(InvalidScopeException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Invalid Scopes");
        pd.setProperty("reason", ex.getReason().name());
        pd.setProperty("invalidScopes", ex.getInvalidScopes());
        return pd;
    }

    @ExceptionHandler(TenantNotAllowedException.class)
    public ProblemDetail handleTenantNotAllowed(TenantNotAllowedException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Tenant Not Allowed");
        return pd;
    }

    @ExceptionHandler(ConcurrentTokenModificationException.class)
    public ProblemDetail handleConflict(ConcurrentTokenModificationException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Concurrent Modification");
        return pd;
    }

    @ExceptionHandler({InvalidTokenStateException.class, EmptyScopesException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid Token Request");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getBindingResult().getAllErrors().isEmpty()
                        ? "Validation failed"
                        : ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage()
        );
        pd.setTitle("Validation Failed");
        pd.setProperty("errors", fieldErrors(ex));
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Validation Failed");
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed"
        );
        pd.setTitle("Malformed Request");
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Parameter '%s' has an invalid value".formatted(ex.getName())
        );
        pd.setTitle("Malformed Request");
        return pd;
    }

    private static Map<String, String> fieldErrors(MethodArgumentNotValidException ex) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}
