package ru.openapi.tokens.token.scope;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ru.openapi.tokens.token.ScopeMapping;
import ru.openapi.tokens.token.spi.ScopeMappingRepository;
import ru.openapi.tokens.token.spi.ScopeResolver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScopesResolutionSteps {

    private String mode;
    private final Map<String, Set<String>> configMappings = new HashMap<>();
    private final Map<String, Set<String>> dbMappings = new HashMap<>();
    private Set<String> tokenScopes = Set.of();
    private Set<String> authorities = Set.of();

    @Given("scope mode is {string}")
    public void scopeModeIs(String mode) {
        this.mode = mode;
    }

    @Given("config mapping {string} to {string}")
    public void configMapping(String tokenScope, String projectScopes) {
        configMappings.put(tokenScope, split(projectScopes));
    }

    @Given("DB mapping {string} to {string} for tenant {string}")
    public void dbMapping(String tokenScope, String projectScopes, String tenantId) {
        dbMappings.put(tokenScope + "|" + tenantId, split(projectScopes));
    }

    @Given("a token with scopes {string}")
    public void aTokenWithScopes(String scopes) {
        tokenScopes = split(scopes);
    }

    @When("scopes are resolved for tenant {string}")
    public void scopesAreResolved(String tenantId) {
        final ScopeResolver resolver = createResolver(tenantId);
        authorities = resolver.resolve(tokenScopes, tenantId);
    }

    @Then("the authorities should be {string}")
    public void theAuthoritiesShouldBe(String expected) {
        assertThat(authorities).containsExactlyInAnyOrderElementsOf(split(expected));
    }

    private ScopeResolver createResolver(String tenantId) {
        if ("identity".equals(mode)) {
            return new IdentityScopeResolver();
        }
        final ScopeMappingRepository repository = mock(ScopeMappingRepository.class);
        when(repository.findByTokenScopes(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            final Iterable<String> scopes = invocation.getArgument(0);
            final String tenant = invocation.getArgument(1);
            final List<ScopeMapping> result = new java.util.ArrayList<>();
            for (String scope : scopes) {
                final Set<String> mapped = dbMappings.get(scope + "|" + tenant);
                if (mapped != null) {
                    for (String project : mapped) {
                        result.add(new ScopeMapping(UUID.randomUUID(), scope, project, tenant));
                    }
                }
            }
            return result;
        });
        return new MappedScopeResolver(new CompositeMappingSource(configMappings, repository));
    }

    private static Set<String> split(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
