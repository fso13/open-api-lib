package ru.openapi.tokens.token.service;

import ru.openapi.tokens.token.spi.AuditRecorder;
import ru.openapi.tokens.token.spi.CallerAuthorityResolver;
import ru.openapi.tokens.token.spi.ScopeValidator;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of {@link DefaultApiTokenService}: quota, default TTL, tenant policy, scope
 * validation and audit hooks. Keeps the service constructor stable while the feature set grows.
 *
 * @param prefixLength       public token prefix length (4…16)
 * @param maxTokensPerOwner  quota of usable tokens per owner
 * @param defaultTtl         TTL applied when the client does not request any expiry, {@code null} = never expires
 * @param allowTenantOverride whether a client may create a token for a tenant other than its own
 * @param scopeValidator     scope validation policy, never {@code null}
 * @param callerAuthorityResolver caller authorities for scope validation, {@code null} = none
 * @param auditRecorder      lifecycle audit hook, {@code null} = disabled
 */
public record ApiTokenServiceSettings(
        int prefixLength,
        int maxTokensPerOwner,
        Duration defaultTtl,
        boolean allowTenantOverride,
        ScopeValidator scopeValidator,
        CallerAuthorityResolver callerAuthorityResolver,
        AuditRecorder auditRecorder
) {

    public ApiTokenServiceSettings {
        if (prefixLength <= 0) {
            throw new IllegalArgumentException("prefixLength must be positive");
        }
        if (maxTokensPerOwner <= 0) {
            throw new IllegalArgumentException("maxTokensPerOwner must be positive");
        }
        if (defaultTtl != null && (defaultTtl.isNegative() || defaultTtl.isZero())) {
            throw new IllegalArgumentException("defaultTtl must be positive");
        }
        scopeValidator = scopeValidator == null ? ScopeValidator.allowAll() : scopeValidator;
    }

    /**
     * Backward compatible defaults: no scope validation, no tenant override, no default TTL, no audit.
     */
    public static ApiTokenServiceSettings defaults(int prefixLength, int maxTokensPerOwner) {
        return new ApiTokenServiceSettings(
                prefixLength,
                maxTokensPerOwner,
                null,
                false,
                ScopeValidator.allowAll(),
                CallerAuthorityResolver.none(),
                null
        );
    }

    public static Builder builder(int prefixLength, int maxTokensPerOwner) {
        return new Builder(prefixLength, maxTokensPerOwner);
    }

    public Builder toBuilder() {
        return new Builder(prefixLength, maxTokensPerOwner)
                .defaultTtl(defaultTtl)
                .allowTenantOverride(allowTenantOverride)
                .scopeValidator(scopeValidator)
                .callerAuthorityResolver(callerAuthorityResolver)
                .auditRecorder(auditRecorder);
    }

    /**
     * Fluent builder, mainly to keep the service constructors readable.
     */
    public static final class Builder {
        private final int prefixLength;
        private final int maxTokensPerOwner;
        private Duration defaultTtl;
        private boolean allowTenantOverride;
        private ScopeValidator scopeValidator = ScopeValidator.allowAll();
        private CallerAuthorityResolver callerAuthorityResolver = CallerAuthorityResolver.none();
        private AuditRecorder auditRecorder;

        private Builder(int prefixLength, int maxTokensPerOwner) {
            this.prefixLength = prefixLength;
            this.maxTokensPerOwner = maxTokensPerOwner;
        }

        public Builder defaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
            return this;
        }

        public Builder allowTenantOverride(boolean allowTenantOverride) {
            this.allowTenantOverride = allowTenantOverride;
            return this;
        }

        public Builder scopeValidator(ScopeValidator scopeValidator) {
            this.scopeValidator = Objects.requireNonNull(scopeValidator, "scopeValidator");
            return this;
        }

        public Builder callerAuthorityResolver(CallerAuthorityResolver resolver) {
            this.callerAuthorityResolver = resolver == null ? CallerAuthorityResolver.none() : resolver;
            return this;
        }

        public Builder auditRecorder(AuditRecorder auditRecorder) {
            this.auditRecorder = auditRecorder;
            return this;
        }

        public ApiTokenServiceSettings build() {
            return new ApiTokenServiceSettings(
                    prefixLength,
                    maxTokensPerOwner,
                    defaultTtl,
                    allowTenantOverride,
                    scopeValidator,
                    callerAuthorityResolver,
                    auditRecorder
            );
        }
    }
}
