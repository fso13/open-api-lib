package ru.openapi.tokens.token.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.ScopeValidationContext;
import ru.openapi.tokens.token.exception.InvalidScopeException;
import ru.openapi.tokens.token.spi.ScopeCatalog;
import ru.openapi.tokens.token.spi.ScopeResolver;
import ru.openapi.tokens.token.spi.ScopeValidator;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Scope validators")
class ScopeValidatorTest {

    private static ScopeValidationContext context(Set<String> requested) {
        return new ScopeValidationContext("owner-1", null, requested, Set.of(), false);
    }

    @Nested
    @DisplayName("NoOpScopeValidator")
    class NoOp {

        @Test
        @DisplayName("Should accept anything")
        void shouldAcceptAnything() {
            assertThatCode(() -> new NoOpScopeValidator().validate(context(Set.of("ROLE_ADMIN"))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("DenyPatternScopeValidator")
    class DenyPattern {

        @Test
        @DisplayName("Should reject scopes matching the denied pattern")
        void shouldRejectDeniedPattern() {
            final ScopeValidator validator = new DenyPatternScopeValidator(List.of("ROLE_.*", "SCOPE_.*"));

            assertThatThrownBy(() -> validator.validate(context(Set.of("payments:read", "ROLE_ADMIN"))))
                    .isInstanceOf(InvalidScopeException.class)
                    .satisfies(ex -> {
                        final InvalidScopeException invalid = (InvalidScopeException) ex;
                        assertThat(invalid.getReason()).isEqualTo(InvalidScopeException.Reason.DENIED_SCOPE);
                        assertThat(invalid.getInvalidScopes()).containsExactly("ROLE_ADMIN");
                    });
        }

        @Test
        @DisplayName("Should accept scopes that do not match")
        void shouldAcceptAllowed() {
            final ScopeValidator validator = new DenyPatternScopeValidator(List.of("ROLE_.*"));

            assertThatCode(() -> validator.validate(context(Set.of("payments:read"))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should ignore blank patterns")
        void shouldIgnoreBlankPatterns() {
            final ScopeValidator validator = new DenyPatternScopeValidator(List.of("  ", ""));

            assertThatCode(() -> validator.validate(context(Set.of("ROLE_ADMIN"))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("CatalogScopeValidator")
    class Catalog {

        private ScopeCatalog catalog(String... scopes) {
            return () -> List.of(scopes).stream().map(scope -> new ScopeInfo(scope, null, Set.of())).toList();
        }

        @Test
        @DisplayName("Should reject scopes missing from the catalog")
        void shouldRejectUnknown() {
            final ScopeValidator validator = new CatalogScopeValidator(catalog("payments:read"));

            assertThatThrownBy(() -> validator.validate(context(Set.of("payments:read", "super:scope"))))
                    .isInstanceOf(InvalidScopeException.class)
                    .satisfies(ex -> assertThat(((InvalidScopeException) ex).getReason())
                            .isEqualTo(InvalidScopeException.Reason.UNKNOWN_SCOPE));
        }

        @Test
        @DisplayName("Should accept catalog scopes")
        void shouldAcceptKnown() {
            final ScopeValidator validator = new CatalogScopeValidator(catalog("payments:read", "demo:read"));

            assertThatCode(() -> validator.validate(context(Set.of("payments:read"))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("CallerAuthorityScopeValidator")
    class CallerAuthority {

        @Test
        @DisplayName("Should reject authorities the caller does not hold")
        void shouldRejectNotGrantable() {
            final ScopeResolver identity = (scopes, tenant) -> scopes;
            final ScopeValidator validator = new CallerAuthorityScopeValidator(identity);
            final ScopeValidationContext ctx = new ScopeValidationContext(
                    "owner-1", null, Set.of("payments:read"), Set.of("reports:read"), false
            );

            assertThatThrownBy(() -> validator.validate(ctx))
                    .isInstanceOf(InvalidScopeException.class)
                    .satisfies(ex -> assertThat(((InvalidScopeException) ex).getInvalidScopes())
                            .containsExactly("payments:read"));
        }

        @Test
        @DisplayName("Should allow a subset of the caller authorities")
        void shouldAllowSubset() {
            final ScopeResolver identity = (scopes, tenant) -> scopes;
            final ScopeValidator validator = new CallerAuthorityScopeValidator(identity);
            final ScopeValidationContext ctx = new ScopeValidationContext(
                    "owner-1", null, Set.of("payments:read"), Set.of("payments:read", "reports:read"), false
            );

            assertThatCode(() -> validator.validate(ctx)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should bypass the check for privileged callers")
        void shouldBypassPrivileged() {
            final ScopeResolver identity = (scopes, tenant) -> scopes;
            final ScopeValidator validator = new CallerAuthorityScopeValidator(identity);
            final ScopeValidationContext ctx = new ScopeValidationContext(
                    "owner-1", null, Set.of("admin:all"), Set.of(), true
            );

            assertThatCode(() -> validator.validate(ctx)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should check projected authorities in mapped mode")
        void shouldCheckProjectedAuthorities() {
            final ScopeResolver mapped = (scopes, tenant) -> Set.of("createPayment", "readPayment");
            final ScopeValidator validator = new CallerAuthorityScopeValidator(mapped);
            final ScopeValidationContext ctx = new ScopeValidationContext(
                    "owner-1", null, Set.of("payment"), Set.of("createPayment"), false
            );

            assertThatThrownBy(() -> validator.validate(ctx))
                    .isInstanceOf(InvalidScopeException.class)
                    .satisfies(ex -> assertThat(((InvalidScopeException) ex).getInvalidScopes())
                            .containsExactly("readPayment"));
        }
    }

    @Nested
    @DisplayName("CompositeScopeValidator")
    class Composite {

        @Test
        @DisplayName("Should stop at the first rejection")
        void shouldStopAtFirst() {
            final ScopeValidator first = context -> {
                throw new InvalidScopeException(InvalidScopeException.Reason.DENIED_SCOPE, Set.of("ROLE_ADMIN"));
            };
            final ScopeValidator second = new NoOpScopeValidator();

            assertThatThrownBy(() -> new CompositeScopeValidator(List.of(first, second))
                    .validate(context(Set.of("ROLE_ADMIN"))))
                    .isInstanceOf(InvalidScopeException.class);
        }

        @Test
        @DisplayName("Should apply every validator when all pass")
        void shouldApplyAll() {
            final ScopeValidator validator = CompositeScopeValidator.of(
                    new DenyPatternScopeValidator(List.of("ROLE_.*")),
                    new NoOpScopeValidator()
            );

            assertThatCode(() -> validator.validate(context(Set.of("payments:read"))))
                    .doesNotThrowAnyException();
        }
    }
}
