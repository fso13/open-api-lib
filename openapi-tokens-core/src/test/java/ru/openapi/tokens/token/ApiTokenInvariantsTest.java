package ru.openapi.tokens.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.openapi.tokens.token.exception.EmptyScopesException;
import ru.openapi.tokens.token.exception.InvalidTokenStateException;
import ru.openapi.tokens.token.exception.QuotaExceededException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApiToken domain invariants")
class ApiTokenInvariantsTest {

    @Test
    @DisplayName("Should create active token with scopes and credentials")
    void shouldCreateActiveToken() {
        final ApiToken token = sampleBuilder().build();

        assertThat(token.status()).isEqualTo(TokenStatus.ACTIVE);
        assertThat(token.scopes()).containsExactly("payments:read");
        assertThat(token.credentials().prefix()).isEqualTo("abcd1234");
    }

    @Test
    @DisplayName("Should reject empty scopes")
    void shouldRejectEmptyScopes() {
        assertThatThrownBy(() -> sampleBuilder().scopes(Set.of()).build())
                .isInstanceOf(EmptyScopesException.class);
    }

    @Test
    @DisplayName("Should revoke active token")
    void shouldRevokeActiveToken() {
        final Instant revokedAt = Instant.parse("2026-10-07T12:00:00Z");
        final ApiToken revoked = sampleBuilder().build().revoke(revokedAt);

        assertThat(revoked.status()).isEqualTo(TokenStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(revokedAt);
    }

    @Test
    @DisplayName("Should reject revoke of already revoked token")
    void shouldRejectDoubleRevoke() {
        final Instant revokedAt = Instant.parse("2026-10-07T12:00:00Z");
        final ApiToken revoked = sampleBuilder().build().revoke(revokedAt);

        assertThatThrownBy(() -> revoked.revoke(revokedAt.plusSeconds(1)))
                .isInstanceOf(InvalidTokenStateException.class);
    }

    @Test
    @DisplayName("Should block and unblock active token")
    void shouldBlockAndUnblock() {
        final ApiToken blocked = sampleBuilder().build().block();
        assertThat(blocked.status()).isEqualTo(TokenStatus.BLOCKED);

        final ApiToken unblocked = blocked.unblock();
        assertThat(unblocked.status()).isEqualTo(TokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should reject unblock of non-blocked token")
    void shouldRejectUnblockWhenNotBlocked() {
        assertThatThrownBy(() -> sampleBuilder().build().unblock())
                .isInstanceOf(InvalidTokenStateException.class);
    }

    @Test
    @DisplayName("Should create QuotaExceededException with owner and limit")
    void shouldCreateQuotaExceededException() {
        final QuotaExceededException ex = new QuotaExceededException("owner-1", 10);

        assertThat(ex.getOwnerId()).isEqualTo("owner-1");
        assertThat(ex.getMaxTokens()).isEqualTo(10);
        assertThat(ex.getMessage()).contains("owner-1").contains("10");
    }

    private static ApiToken.Builder sampleBuilder() {
        return ApiToken.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .name("ci-token")
                .credentials(new TokenCredentials("abcd1234", "hash-value"))
                .ownerId("owner-1")
                .scopes(Set.of("payments:read"))
                .createdAt(Instant.parse("2026-10-01T00:00:00Z"));
    }
}
