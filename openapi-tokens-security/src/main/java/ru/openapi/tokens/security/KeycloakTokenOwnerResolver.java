package ru.openapi.tokens.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ru.openapi.tokens.token.TokenOwner;
import ru.openapi.tokens.token.spi.TokenOwnerResolver;


/**
 * Resolves the token owner from Keycloak claims.
 *
 * <p>Supported security contexts:</p>
 * <ul>
 *     <li>{@link JwtAuthenticationToken} — OAuth2 resource server (a Keycloak access token, e.g. from
 *     client credentials or direct access grants);</li>
 *     <li>{@link OidcUser} principal — interactive OIDC login ({@code oauth2Login}, authorization
 *     code flow) used by web UIs;</li>
 *     <li>{@link ApiTokenAuthentication} — a request already authenticated by an API token.</li>
 * </ul>
 *
 * <p>The owner id is the {@code sub} claim; the tenant is read from the configured claim
 * ({@code tenant_id} by default).</p>
 */
public final class KeycloakTokenOwnerResolver implements TokenOwnerResolver {

    public static final String DEFAULT_TENANT_CLAIM = "tenant_id";

    private final String tenantClaim;

    public KeycloakTokenOwnerResolver() {
        this(DEFAULT_TENANT_CLAIM);
    }

    /**
     * @param tenantClaim name of the claim holding the tenant id; {@code tenant_id} when blank
     */
    public KeycloakTokenOwnerResolver(String tenantClaim) {
        this.tenantClaim = tenantClaim == null || tenantClaim.isBlank()
                ? DEFAULT_TENANT_CLAIM
                : tenantClaim.trim();
    }

    public String tenantClaim() {
        return tenantClaim;
    }

    @Override
    public TokenOwner resolveCurrentOwner() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            final Jwt jwt = jwtAuth.getToken();
            return new TokenOwner(requireSubject(jwt.getSubject()), jwt.getClaimAsString(tenantClaim));
        }
        if (authentication instanceof ApiTokenAuthentication apiAuth && apiAuth.getApiToken() != null) {
            return new TokenOwner(apiAuth.getApiToken().ownerId(), apiAuth.getApiToken().tenantId());
        }
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return new TokenOwner(requireSubject(oidcUser.getSubject()), oidcUser.getClaimAsString(tenantClaim));
        }
        throw new IllegalStateException(
                "Expected JwtAuthenticationToken, OidcUser or ApiTokenAuthentication for Keycloak owner "
                        + "resolution, got: " + describe(authentication)
        );
    }

    private static String requireSubject(String sub) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("JWT subject (sub) is missing");
        }
        return sub;
    }

    private static String describe(Authentication authentication) {
        if (authentication == null) {
            return "no authentication";
        }
        return authentication.getClass().getSimpleName()
                + " with principal " + authentication.getPrincipal().getClass().getSimpleName();
    }
}
