package ru.openapi.tokens.autoconfigure;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps optimistic locking failures of the token REST controllers to 409.
 *
 * <p>These failures surface when the transaction commits, which happens after the service method
 * returned — too late for {@link TransactionalApiTokenService} to translate them. The advice is
 * scoped to the token web package so it does not interfere with the host application.</p>
 */
@RestControllerAdvice(basePackages = "ru.openapi.tokens.web")
public class TokenConcurrencyExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The token was modified by another request, retry the operation"
        );
        pd.setTitle("Concurrent Modification");
        return pd;
    }
}
