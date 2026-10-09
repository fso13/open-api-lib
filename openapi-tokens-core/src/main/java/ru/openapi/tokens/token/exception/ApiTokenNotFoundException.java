package ru.openapi.tokens.token.exception;

import java.util.UUID;

/**
 * Raised when an API token cannot be found by id or prefix.
 */
public class ApiTokenNotFoundException extends RuntimeException {

    public ApiTokenNotFoundException(UUID id) {
        super("API token not found: " + id);
    }

    public ApiTokenNotFoundException(String prefix) {
        super("API token not found for prefix: " + prefix);
    }
}
