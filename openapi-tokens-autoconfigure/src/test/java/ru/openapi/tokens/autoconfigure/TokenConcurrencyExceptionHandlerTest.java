package ru.openapi.tokens.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenConcurrencyExceptionHandler")
class TokenConcurrencyExceptionHandlerTest {

    private final TokenConcurrencyExceptionHandler handler = new TokenConcurrencyExceptionHandler();

    @Test
    @DisplayName("Should map optimistic locking failures to 409 with a problem detail")
    void shouldMapToConflict() {
        final ProblemDetail detail = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("Token was modified concurrently"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getTitle()).isEqualTo("Concurrent Modification");
        assertThat(detail.getDetail()).contains("retry");
    }
}
