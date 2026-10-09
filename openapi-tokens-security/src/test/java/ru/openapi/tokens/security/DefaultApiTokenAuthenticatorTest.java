package ru.openapi.tokens.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenExpiry;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.ApiTokenService;
import ru.openapi.tokens.token.spi.RateLimiter;
import ru.openapi.tokens.token.spi.ScopeResolver;
import ru.openapi.tokens.token.spi.TokenHasher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultApiTokenAuthenticator")
class DefaultApiTokenAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock private ApiTokenRepository repository;
    @Mock private TokenHasher tokenHasher;
    @Mock private ScopeResolver scopeResolver;
    @Mock private RateLimiter rateLimiter;
    @Mock private ApiTokenService apiTokenService;

    private DefaultApiTokenAuthenticator authenticator;
    private RawToken raw;
    private ApiToken token;

    @BeforeEach
    void setUp() {
        authenticator = new DefaultApiTokenAuthenticator(
                repository, tokenHasher, scopeResolver, rateLimiter, apiTokenService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        raw = RawToken.parse("atk_abcd1234_supersecretvalue");
        token = ApiToken.builder()
                .id(UUID.randomUUID())
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash"))
                .ownerId("owner-1")
                .scopes(Set.of("read"))
                .createdAt(NOW.minusSeconds(60))
                .build();
    }

    @Test
    @DisplayName("Should authenticate valid token with authorities")
    void shouldAuthenticateValid() {
        when(repository.findByPrefix("abcd1234")).thenReturn(Optional.of(token));
        when(tokenHasher.matches("supersecretvalue", "hash")).thenReturn(true);
        when(rateLimiter.tryAcquire(eq(token.id()), any())).thenReturn(true);
        when(scopeResolver.resolve(Set.of("read"), null)).thenReturn(Set.of("read"));
        when(apiTokenService.touchLastUsed(token.id())).thenReturn(token);
        when(repository.findById(token.id())).thenReturn(Optional.of(token));

        final var result = authenticator.authenticate(raw);

        assertThat(result).isPresent();
        assertThat(result.get().authorities()).containsExactly("read");
        verify(apiTokenService).touchLastUsed(token.id());
    }

    @Test
    @DisplayName("Should reject invalid hash")
    void shouldRejectInvalidHash() {
        when(repository.findByPrefix("abcd1234")).thenReturn(Optional.of(token));
        when(tokenHasher.matches("supersecretvalue", "hash")).thenReturn(false);

        assertThat(authenticator.authenticate(raw)).isEmpty();
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }

    @Test
    @DisplayName("Should reject revoked token")
    void shouldRejectRevoked() {
        final ApiToken revoked = token.toBuilder().status(TokenStatus.REVOKED).revokedAt(NOW).build();
        when(repository.findByPrefix("abcd1234")).thenReturn(Optional.of(revoked));

        assertThat(authenticator.authenticate(raw)).isEmpty();
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpired() {
        final ApiToken expired = token.toBuilder()
                .expiry(TokenExpiry.ofAbsolute(NOW.minusSeconds(1)).withCreatedAt(NOW.minusSeconds(120)))
                .build();
        when(repository.findByPrefix("abcd1234")).thenReturn(Optional.of(expired));

        assertThat(authenticator.authenticate(raw)).isEmpty();
        verify(tokenHasher, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Should throw when rate limited")
    void shouldThrowWhenRateLimited() {
        when(repository.findByPrefix("abcd1234")).thenReturn(Optional.of(token));
        when(tokenHasher.matches("supersecretvalue", "hash")).thenReturn(true);
        when(rateLimiter.tryAcquire(eq(token.id()), any())).thenReturn(false);

        assertThatThrownBy(() -> authenticator.authenticate(raw))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
