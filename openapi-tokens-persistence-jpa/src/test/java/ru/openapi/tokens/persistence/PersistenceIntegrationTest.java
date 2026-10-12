package ru.openapi.tokens.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.ScopeCatalogEntry;
import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.AuditLogRepository;
import ru.openapi.tokens.token.spi.ScopeCatalogRepository;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceTestApplication.class)
@Testcontainers
@DisplayName("JPA persistence integration")
class PersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("openapi_tokens")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @Autowired
    private ScopeMappingRepository scopeMappingRepository;

    @Autowired
    private ScopeCatalogRepository scopeCatalogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("Should save token and find by prefix with scopes")
    void shouldSaveAndFindByPrefix() {
        final UUID id = UUID.randomUUID();
        final ApiToken token = ApiToken.builder()
                .id(id)
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash-1"))
                .ownerId("owner-1")
                .scopes(Set.of("payments:read", "payments:write"))
                .createdAt(Instant.parse("2026-10-07T10:00:00Z"))
                .build();

        apiTokenRepository.save(token);

        final ApiToken found = apiTokenRepository.findByPrefix("abcd1234").orElseThrow();
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.status()).isEqualTo(TokenStatus.ACTIVE);
        assertThat(found.scopes()).containsExactlyInAnyOrder("payments:read", "payments:write");
        assertThat(found.credentials().tokenHash()).isEqualTo("hash-1");
    }

    @Test
    @DisplayName("Should persist scope mapping and query by token scopes")
    void shouldPersistScopeMapping() {
        final ScopeMapping mapping = new ScopeMapping(
                UUID.randomUUID(),
                "payment",
                "createPayment",
                "tenant-a"
        );
        scopeMappingRepository.save(mapping);

        assertThat(scopeMappingRepository.findByTokenScopes(Set.of("payment"), "tenant-a"))
                .extracting(ScopeMapping::projectScope)
                .containsExactly("createPayment");
        assertThat(scopeMappingRepository.findAll())
                .extracting(ScopeMapping::tokenScope)
                .contains("payment");
    }

    @Test
    @DisplayName("Should upsert a scope mapping by its natural key instead of duplicating it")
    void shouldUpsertScopeMappingByNaturalKey() {
        // Two saves of the same logical mapping: tenant_id IS NULL previously produced duplicates.
        scopeMappingRepository.save(new ScopeMapping(null, "report", "readReport", null));
        scopeMappingRepository.save(new ScopeMapping(null, "report", "readReport", null));

        assertThat(scopeMappingRepository.findAll())
                .filteredOn(mapping -> "report".equals(mapping.tokenScope()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Should store, update and delete scope catalog descriptions")
    void shouldStoreScopeCatalog() {
        scopeCatalogRepository.save(new ScopeCatalogEntry("payments:read", "Чтение платежей"));
        scopeCatalogRepository.save(new ScopeCatalogEntry("payments:write", "Изменение платежей"));

        assertThat(scopeCatalogRepository.findAll())
                .extracting(ScopeCatalogEntry::scope)
                .containsExactlyInAnyOrder("payments:read", "payments:write");

        scopeCatalogRepository.save(new ScopeCatalogEntry("payments:read", "Только чтение"));

        assertThat(scopeCatalogRepository.findAll())
                .filteredOn(entry -> "payments:read".equals(entry.scope()))
                .singleElement()
                .extracting(ScopeCatalogEntry::description)
                .isEqualTo("Только чтение");

        scopeCatalogRepository.deleteByScope("payments:write");

        assertThat(scopeCatalogRepository.findAll())
                .extracting(ScopeCatalogEntry::scope)
                .containsExactly("payments:read");
    }

    @Test
    @DisplayName("Should append audit event")
    void shouldAppendAudit() {
        final UUID tokenId = UUID.randomUUID();
        auditLogRepository.append(new AuditEvent(
                tokenId,
                "owner-1",
                null,
                "GET",
                "/api/payments",
                "AUTHENTICATE",
                true,
                200,
                "127.0.0.1",
                "junit",
                Instant.parse("2026-10-07T11:00:00Z")
        ));

        assertThat(auditLogRepository.findByTokenId(tokenId, 0, 10)).hasSize(1);
        assertThat(auditLogRepository.usageStats(tokenId).totalRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should assign an optimistic locking version and increment it on update")
    void shouldTrackVersion() {
        final ApiToken saved = apiTokenRepository.save(sampleToken(UUID.randomUUID(), "version-owner", "aaaa0001"));
        assertThat(saved.version()).isNotNull();
        entityManager.flush();
        final long before = saved.version();

        final ApiToken revoked = apiTokenRepository.save(saved.revoke(Instant.parse("2026-10-07T12:00:00Z")));
        entityManager.flush();
        final ApiToken reloaded = apiTokenRepository.findById(revoked.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(TokenStatus.REVOKED);
        assertThat(reloaded.version()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Should not resurrect a revoked token when lastUsedAt is touched")
    void shouldNotResurrectRevokedToken() {
        final ApiToken saved = apiTokenRepository.save(sampleToken(UUID.randomUUID(), "race-owner", "aaaa0002"));
        final ApiToken revoked = apiTokenRepository.save(saved.revoke(Instant.parse("2026-10-07T12:00:00Z")));

        final boolean touched = apiTokenRepository.touchLastUsed(revoked.id(), Instant.parse("2026-10-07T13:00:00Z"));

        assertThat(touched).isFalse();
        assertThat(apiTokenRepository.findById(revoked.id()).orElseThrow().status())
                .isEqualTo(TokenStatus.REVOKED);
    }

    @Test
    @DisplayName("Should touch lastUsedAt for an active token only")
    void shouldTouchActiveToken() {
        final ApiToken saved = apiTokenRepository.save(sampleToken(UUID.randomUUID(), "touch-owner", "aaaa0003"));
        final Instant at = Instant.parse("2026-10-07T13:30:00Z");

        assertThat(apiTokenRepository.touchLastUsed(saved.id(), at)).isTrue();
        assertThat(apiTokenRepository.findById(saved.id()).orElseThrow().expiry().lastUsedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("Should count only quota consuming statuses per owner")
    void shouldCountUsableTokens() {
        final ApiToken active = apiTokenRepository.save(
                sampleToken(UUID.randomUUID(), "quota-owner", "bbbb0001"));
        apiTokenRepository.save(sampleToken(UUID.randomUUID(), "quota-owner", "bbbb0002")
                .revoke(Instant.parse("2026-10-07T12:00:00Z")));

        assertThat(apiTokenRepository.countByOwnerId("quota-owner")).isEqualTo(2);
        assertThat(apiTokenRepository.countUsableByOwnerId("quota-owner")).isEqualTo(1);

        apiTokenRepository.save(active.revoke(Instant.parse("2026-10-07T12:30:00Z")));

        assertThat(apiTokenRepository.countUsableByOwnerId("quota-owner")).isZero();
    }

    @Test
    @DisplayName("Should page audit entries by a true offset")
    void shouldPageAuditByOffset() {
        final UUID tokenId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            auditLogRepository.append(new AuditEvent(
                    tokenId, "owner-1", null, "GET", "/api/payments/" + i, "AUTHENTICATE",
                    true, 200, "127.0.0.1", "junit",
                    Instant.parse("2026-10-07T11:0%d:00Z".formatted(i))
            ));
        }

        // newest first: 11:04, 11:03, 11:02, 11:01, 11:00
        assertThat(auditLogRepository.findByTokenId(tokenId, 2, 2))
                .extracting(AuditEvent::endpoint)
                .containsExactly("/api/payments/2", "/api/payments/1");
        assertThat(auditLogRepository.findByTokenId(tokenId, 4, 2))
                .extracting(AuditEvent::endpoint)
                .containsExactly("/api/payments/0");
    }

    @Test
    @DisplayName("Should truncate oversized audit fields instead of losing the event")
    void shouldTruncateOversizedAuditFields() {
        final UUID tokenId = UUID.randomUUID();
        auditLogRepository.append(new AuditEvent(
                tokenId, "owner-1", null, "GET", "/" + "x".repeat(900), "AUTHENTICATE",
                true, 200, "127.0.0.1", "agent".repeat(300),
                Instant.parse("2026-10-07T11:00:00Z")
        ));

        final AuditEvent stored = auditLogRepository.findByTokenId(tokenId, 0, 10).getFirst();
        assertThat(stored.endpoint()).hasSize(512);
        assertThat(stored.userAgent()).hasSize(512);
    }

    private static ApiToken sampleToken(UUID id, String ownerId, String prefix) {
        return ApiToken.builder()
                .id(id)
                .name("ci")
                .credentials(new TokenCredentials(prefix, "hash-" + prefix))
                .ownerId(ownerId)
                .scopes(Set.of("payments:read"))
                .createdAt(Instant.parse("2026-10-07T10:00:00Z"))
                .build();
    }
}
