package ru.openapi.tokens.sample.keycloak;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.openapi.tokens.security.ApiTokenAuthentication;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protected demo resources: shows who Keycloak (or an API token) authenticated and demonstrates
 * both authorization sources — Keycloak roles ({@code hasRole('ADMIN')}) and API token scopes
 * ({@code hasAuthority('payments:read')}).
 */
@RestController
@RequestMapping("/api/demo")
public class KeycloakDemoController {

    @GetMapping("/whoami")
    public Map<String, Object> whoami(Authentication authentication) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("principal", authentication.getName());
        body.put("authenticationType", authentication.getClass().getSimpleName());
        body.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList());

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            final Jwt jwt = jwtAuthentication.getToken();
            body.put("source", "keycloak-access-token");
            body.put("sub", jwt.getSubject());
            body.put("preferredUsername", jwt.getClaimAsString("preferred_username"));
            body.put("tenantId", jwt.getClaimAsString("tenant_id"));
            body.put("issuer", String.valueOf(jwt.getIssuer()));
        } else if (authentication instanceof ApiTokenAuthentication apiTokenAuthentication
                && apiTokenAuthentication.getApiToken() != null) {
            body.put("source", "api-token");
            body.put("apiTokenId", apiTokenAuthentication.getApiToken().id().toString());
            body.put("ownerId", apiTokenAuthentication.getApiToken().ownerId());
            body.put("tenantId", apiTokenAuthentication.getApiToken().tenantId());
            body.put("scopes", apiTokenAuthentication.getApiToken().scopes());
        }
        return body;
    }

    /**
     * Requires the API token scope {@code payments:read} (a Keycloak access token alone does not
     * carry this authority — that is the point of the token service).
     */
    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('payments:read')")
    public Map<String, String> payments() {
        return Map.of("resource", "payments", "access", "granted", "grantedBy", "api-token scope");
    }

    /**
     * Requires the Keycloak realm role {@code ADMIN}.
     */
    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> reports() {
        return Map.of("resource", "reports", "access", "granted", "grantedBy", "keycloak realm role ADMIN");
    }
}
