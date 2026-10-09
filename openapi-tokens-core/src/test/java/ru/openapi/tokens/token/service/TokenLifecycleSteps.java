package ru.openapi.tokens.token.service;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.CreatedApiToken;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.TokenOwner;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.command.CreateTokenCommand;
import ru.openapi.tokens.token.command.RevokeTokenCommand;
import ru.openapi.tokens.token.exception.QuotaExceededException;
import ru.openapi.tokens.token.spi.ApiTokenRepository;
import ru.openapi.tokens.token.spi.TokenHasher;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenLifecycleSteps {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private ApiTokenRepository repository;
    private TokenHasher tokenHasher;
    private TokenOwnerResolver ownerResolver;
    private DefaultApiTokenService service;
    private int maxTokens = 10;
    private String ownerId;
    private CreatedApiToken created;
    private ApiToken current;
    private Exception error;

    @Given("an owner {string} with {int} existing tokens")
    public void ownerWithTokens(String ownerId, int count) {
        this.ownerId = ownerId;
        initMocks();
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of(ownerId));
        when(repository.countByOwnerId(ownerId)).thenReturn((long) count);
        when(tokenHasher.hash(anyString())).thenReturn("hashed");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Given("the max tokens per owner is {int}")
    public void maxTokens(int max) {
        this.maxTokens = max;
        if (repository != null) {
            service = new DefaultApiTokenService(
                    repository, tokenHasher, ownerResolver,
                    Clock.fixed(NOW, ZoneOffset.UTC), 8, maxTokens
            );
        }
    }

    @Given("an owner {string} owns an active token")
    public void ownerOwnsToken(String ownerId) {
        this.ownerId = ownerId;
        initMocks();
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of(ownerId));
        current = ApiToken.builder()
                .id(UUID.randomUUID())
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash"))
                .ownerId(ownerId)
                .scopes(Set.of("read"))
                .createdAt(NOW.minusSeconds(10))
                .build();
        when(repository.findById(current.id())).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new DefaultApiTokenService(
                repository, tokenHasher, ownerResolver,
                Clock.fixed(NOW, ZoneOffset.UTC), 8, maxTokens
        );
    }

    @When("the owner creates a token named {string} with scopes {string}")
    public void createToken(String name, String scopesCsv) {
        service = new DefaultApiTokenService(
                repository, tokenHasher, ownerResolver,
                Clock.fixed(NOW, ZoneOffset.UTC), 8, maxTokens
        );
        try {
            created = service.create(new CreateTokenCommand(
                    name, null, split(scopesCsv), null, null, null, null
            ));
            current = created.token();
        } catch (Exception ex) {
            error = ex;
        }
    }

    @When("the owner revokes the token")
    public void revokeToken() {
        current = service.revoke(new RevokeTokenCommand(current.id()));
    }

    @Then("the raw token should be returned once")
    public void rawReturned() {
        assertThat(error).isNull();
        assertThat(created.rawToken()).isInstanceOf(RawToken.class);
        assertThat(created.rawToken().value()).startsWith("atk_");
    }

    @Then("the stored token should contain a hash not the secret")
    public void storedHashOnly() {
        assertThat(created.token().credentials().tokenHash()).isEqualTo("hashed");
        assertThat(created.token().credentials().tokenHash())
                .isNotEqualTo(created.rawToken().secret());
    }

    @Then("the token status should be {string}")
    public void statusShouldBe(String status) {
        assertThat(current.status()).isEqualTo(TokenStatus.valueOf(status));
    }

    @Then("token creation should fail with quota exceeded")
    public void quotaExceeded() {
        assertThat(error).isInstanceOf(QuotaExceededException.class);
    }

    private void initMocks() {
        repository = mock(ApiTokenRepository.class);
        tokenHasher = mock(TokenHasher.class);
        ownerResolver = mock(TokenOwnerResolver.class);
    }

    private static Set<String> split(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
