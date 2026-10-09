package ru.openapi.tokens.token.exception;

/**
 * Raised when a token is created or updated without at least one scope.
 */
public class EmptyScopesException extends RuntimeException {

    public EmptyScopesException() {
        super("Token must have at least one scope");
    }
}
