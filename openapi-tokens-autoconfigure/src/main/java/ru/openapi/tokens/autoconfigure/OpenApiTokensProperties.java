package ru.openapi.tokens.autoconfigure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import ru.openapi.tokens.token.hash.HashingAlgorithm;
import ru.openapi.tokens.token.scope.ScopeMode;
import ru.openapi.tokens.token.scope.ScopeValidationMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "openapi.tokens")
public class OpenApiTokensProperties {

    private boolean enabled = true;
    private final Auth auth = new Auth();
    private final Scopes scopes = new Scopes();
    private final Hashing hashing = new Hashing();
    private final RateLimit rateLimit = new RateLimit();
    private final Audit audit = new Audit();
    private final Limits limits = new Limits();
    private final Token token = new Token();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Auth getAuth() {
        return auth;
    }

    public Scopes getScopes() {
        return scopes;
    }

    public Hashing getHashing() {
        return hashing;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Audit getAudit() {
        return audit;
    }

    public Limits getLimits() {
        return limits;
    }

    public Token getToken() {
        return token;
    }

    public static class Auth {
        @NotBlank
        private String mode = "internal";
        /**
         * Reject tokens that resolve to no authorities at all (a token that grants nothing should
         * not authenticate).
         */
        private boolean rejectEmptyAuthorities = true;
        /**
         * Failed authentication attempts allowed per token prefix and window. {@code 0} disables
         * pre-authentication throttling.
         */
        @Min(0)
        private int failedAttemptLimit = 60;
        @Min(1)
        private int failedAttemptWindowSeconds = 60;
        private final Keycloak keycloak = new Keycloak();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isRejectEmptyAuthorities() {
            return rejectEmptyAuthorities;
        }

        public void setRejectEmptyAuthorities(boolean rejectEmptyAuthorities) {
            this.rejectEmptyAuthorities = rejectEmptyAuthorities;
        }

        public int getFailedAttemptLimit() {
            return failedAttemptLimit;
        }

        public void setFailedAttemptLimit(int failedAttemptLimit) {
            this.failedAttemptLimit = failedAttemptLimit;
        }

        public int getFailedAttemptWindowSeconds() {
            return failedAttemptWindowSeconds;
        }

        public void setFailedAttemptWindowSeconds(int failedAttemptWindowSeconds) {
            this.failedAttemptWindowSeconds = failedAttemptWindowSeconds;
        }

        public Keycloak getKeycloak() {
            return keycloak;
        }
    }

    /**
     * Keycloak specific settings, used when {@code openapi.tokens.auth.mode=keycloak}.
     */
    public static class Keycloak {
        /**
         * Claim carrying the tenant id of the owner ({@code sub} always carries the owner id).
         */
        @NotBlank
        private String tenantClaim = "tenant_id";

        public String getTenantClaim() {
            return tenantClaim;
        }

        public void setTenantClaim(String tenantClaim) {
            this.tenantClaim = tenantClaim;
        }
    }

    public static class Scopes {
        @NotNull
        private ScopeMode mode = ScopeMode.IDENTITY;
        /**
         * Allow a client to create a token for a tenant other than its own. Off by default: the
         * tenant is taken from the authenticated owner.
         */
        private boolean allowTenantOverride = false;
        private final Validation validation = new Validation();
        private Map<String, Set<String>> mappings = new HashMap<>();
        /**
         * Catalog of token scopes: scope name → human readable description. Rendered by the
         * scope reference UI and served by {@code GET /api/openapi/scopes}. Descriptions stored in
         * the {@code scope_catalog} table win over these values.
         *
         * <p>Scope names containing {@code :} must use the bracket notation in {@code .yml}
         * ({@code "[payments:read]": "..."}), otherwise Spring Boot drops the colon while binding
         * the map key.</p>
         */
        private Map<String, String> catalog = new HashMap<>();

        public ScopeMode getMode() {
            return mode;
        }

        public void setMode(ScopeMode mode) {
            this.mode = mode;
        }

        public boolean isAllowTenantOverride() {
            return allowTenantOverride;
        }

        public void setAllowTenantOverride(boolean allowTenantOverride) {
            this.allowTenantOverride = allowTenantOverride;
        }

        public Validation getValidation() {
            return validation;
        }

        public Map<String, Set<String>> getMappings() {
            return mappings;
        }

        public void setMappings(Map<String, Set<String>> mappings) {
            this.mappings = mappings;
        }

        public Map<String, String> getCatalog() {
            return catalog;
        }

        public void setCatalog(Map<String, String> catalog) {
            this.catalog = catalog;
        }
    }

    /**
     * Scope validation performed when a token is created.
     */
    public static class Validation {
        /**
         * {@code off | deny-list | catalog | strict}. Defaults to {@code catalog}: requested scopes
         * must be known scopes and must not match a denied pattern.
         */
        @NotNull
        private ScopeValidationMode mode = ScopeValidationMode.CATALOG;
        /**
         * Scopes that can never be granted, for example Spring Security roles.
         */
        private List<String> denyPatterns = new ArrayList<>(List.of("ROLE_.*"));
        /**
         * Authorities allowed to delegate any scope (only relevant for {@code strict}).
         */
        private Set<String> privilegedAuthorities = new LinkedHashSet<>(Set.of("ROLE_ADMIN"));

        public ScopeValidationMode getMode() {
            return mode;
        }

        public void setMode(ScopeValidationMode mode) {
            this.mode = mode;
        }

        public List<String> getDenyPatterns() {
            return denyPatterns;
        }

        public void setDenyPatterns(List<String> denyPatterns) {
            this.denyPatterns = denyPatterns;
        }

        public Set<String> getPrivilegedAuthorities() {
            return privilegedAuthorities;
        }

        public void setPrivilegedAuthorities(Set<String> privilegedAuthorities) {
            this.privilegedAuthorities = privilegedAuthorities;
        }
    }

    public static class Hashing {
        @NotNull
        private HashingAlgorithm algorithm = HashingAlgorithm.ARGON2;

        public HashingAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(HashingAlgorithm algorithm) {
            this.algorithm = algorithm;
        }
    }

    public static class RateLimit {
        @NotBlank
        private String backend = "memory";
        /**
         * Value of the {@code Retry-After} header sent with 429 responses.
         */
        @Min(1)
        @Max(86_400)
        private int retryAfterSeconds = 60;

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public void setRetryAfterSeconds(int retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static class Audit {
        private boolean enabled = true;
        private boolean async = true;
        private boolean recordFailures = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public boolean isRecordFailures() {
            return recordFailures;
        }

        public void setRecordFailures(boolean recordFailures) {
            this.recordFailures = recordFailures;
        }
    }

    public static class Limits {
        @Min(1)
        private int maxTokensPerOwner = 10;

        public int getMaxTokensPerOwner() {
            return maxTokensPerOwner;
        }

        public void setMaxTokensPerOwner(int maxTokensPerOwner) {
            this.maxTokensPerOwner = maxTokensPerOwner;
        }
    }

    public static class Token {
        @Min(4)
        private int prefixLength = 8;
        @NotNull
        private Duration defaultTtl = Duration.ofDays(90);
        @NotBlank
        private String header = "Authorization";
        @NotBlank
        private String bearerPrefix = "Bearer ";

        public int getPrefixLength() {
            return prefixLength;
        }

        public void setPrefixLength(int prefixLength) {
            this.prefixLength = prefixLength;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getBearerPrefix() {
            return bearerPrefix;
        }

        public void setBearerPrefix(String bearerPrefix) {
            this.bearerPrefix = bearerPrefix;
        }
    }
}
