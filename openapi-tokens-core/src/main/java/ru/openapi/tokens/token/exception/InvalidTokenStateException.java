package ru.openapi.tokens.token.exception;

import ru.openapi.tokens.token.TokenStatus;

/**
 * Raised when a lifecycle transition is not allowed for the current status.
 */
public class InvalidTokenStateException extends RuntimeException {

    private final TokenStatus currentStatus;

    public InvalidTokenStateException(String message, TokenStatus currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public TokenStatus getCurrentStatus() {
        return currentStatus;
    }
}
