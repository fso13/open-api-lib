package ru.openapi.tokens.token.exception;

/**
 * Raised when a token was modified concurrently by another request (optimistic locking conflict).
 */
public class ConcurrentTokenModificationException extends RuntimeException {

    public ConcurrentTokenModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
