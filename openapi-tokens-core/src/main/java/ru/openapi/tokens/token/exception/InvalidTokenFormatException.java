package ru.openapi.tokens.token.exception;

/**
 * Raised when a raw token string does not match {@code atk_<prefix>_<secret>}.
 */
public class InvalidTokenFormatException extends RuntimeException {

    public InvalidTokenFormatException(String message) {
        super(message);
    }
}
