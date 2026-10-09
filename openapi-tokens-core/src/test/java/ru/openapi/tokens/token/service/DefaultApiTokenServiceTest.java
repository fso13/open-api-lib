package ru.openapi.tokens.token.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenOwner;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.command.BlockTokenCommand;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.exception.ApiTokenNotFoundException;
import ru.openapi.tokens.token.exception.QuotaExceededException;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultApiTokenService")
class DefaultApiTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private ApiTokenRepository repository;
    @Mock
    private TokenHasher tokenHasher;
    @Mock
    private TokenOwnerResolver ownerResolver;

    private DefaultApiTokenService service;

    @BeforeEach
    void setUp() {
        service = new DefaultApiTokenService(
                repository,
                tokenHasher,
                ownerResolver,
                Clock.fixed(NOW, ZoneOffset.UTC),
                8,
                10
        );
    }

    @Test
    @DisplayName("Should create token returning raw once and store hash only")
    void shouldCreateToken() {
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of("owner-1"));
        when(repository.countByOwnerId("owner-1")).thenReturn(0L);
        when(tokenHasher.hash(any())).thenReturn("hashed-secret");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final CreatedApiToken created = service.create(CreateTokenCommand.of("ci", Set.of("read")));

        assertThat(created.rawToken().value()).startsWith("atk_");
        assertThat(created.token().credentials().tokenHash()).isEqualTo("hashed-secret");
        assertThat(created.token().credentials().prefix()).isEqualTo(created.rawToken().prefix());
        assertThat(created.token().status()).isEqualTo(TokenStatus.ACTIVE);

        final ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().credentials().tokenHash()).isEqualTo("hashed-secret");
        assertThat(captor.getValue().credentials().prefix()).doesNotContain(created.rawToken().secret());
    }

    @Test
    @DisplayName("Should reject create when quota exceeded")
    void shouldRejectQuotaExceeded() {
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of("owner-1"));
        when(repository.countByOwnerId("owner-1")).thenReturn(10L);

        assertThatThrownBy(() -> service.create(CreateTokenCommand.of("ci", Set.of("read"))))
                .isInstanceOf(QuotaExceededException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should revoke own token")
    void shouldRevoke() {
        final UUID id = UUID.randomUUID();
        final ApiToken existing = sampleToken(id, "owner-1");
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of("owner-1"));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final ApiToken revoked = service.revoke(new RevokeTokenCommand(id));

        assertThat(revoked.status()).isEqualTo(TokenStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should block and unblock token")
    void shouldBlockAndUnblock() {
        final UUID id = UUID.randomUUID();
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of("owner-1"));
        when(repository.findById(id)).thenReturn(Optional.of(sampleToken(id, "owner-1")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final ApiToken blocked = service.block(BlockTokenCommand.block(id));
        assertThat(blocked.status()).isEqualTo(TokenStatus.BLOCKED);

        when(repository.findById(id)).thenReturn(Optional.of(blocked));
        final ApiToken unblocked = service.block(BlockTokenCommand.unblock(id));
        assertThat(unblocked.status()).isEqualTo(TokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw when token not found")
    void shouldThrowWhenNotFound() {
        final UUID id = UUID.randomUUID();
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of("owner-1"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ApiTokenNotFoundException.class);
    }

    @Test
    @DisplayName("Should touch lastUsedAt for sliding expiry")
    void shouldTouchLastUsed() {
        final UUID id = UUID.randomUUID();
        final ApiToken existing = sampleToken(id, "owner-1");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final ApiToken touched = service.touchLastUsed(id);

        assertThat(touched.expiry().lastUsedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should list all tokens for admin without ownership filter")
    void shouldListAll() {
        when(repository.findAll()).thenReturn(List.of(
                sampleToken(UUID.randomUUID(), "owner-1"),
                sampleToken(UUID.randomUUID(), "owner-2")
        ));

        assertThat(service.listAll()).hasSize(2);
    }

    @Test
    @DisplayName("Should force revoke any token as admin")
    void shouldForceRevoke() {
        final UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(sampleToken(id, "other-owner")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final ApiToken revoked = service.forceRevoke(new RevokeTokenCommand(id));

        assertThat(revoked.status()).isEqualTo(TokenStatus.REVOKED);
    }

    private static ApiToken sampleToken(UUID id, String ownerId) {
        return ApiToken.builder()
                .id(id)
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash"))
                .ownerId(ownerId)
                .scopes(Set.of("read"))
                .createdAt(NOW.minusSeconds(60))
                .build();
    }
}
