package ru.openapi.tokens.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.openapi.tokens.token.ApiToken;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security authentication representing a validated API token.
 */
public final class ApiTokenAuthentication extends AbstractAuthenticationToken {

    private final ApiToken token;
    private final String rawToken;

    private ApiTokenAuthentication(ApiToken token, String rawToken, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        this.rawToken = rawToken;
        setAuthenticated(token != null);
    }

    public static ApiTokenAuthentication unauthenticated(String rawToken) {
        return new ApiTokenAuthentication(null, rawToken, Set.of());
    }

    public static ApiTokenAuthentication authenticated(ApiToken token, Set<String> authorities) {
        Objects.requireNonNull(token, "token");
        final Set<GrantedAuthority> granted = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
        return new ApiTokenAuthentication(token, null, granted);
    }

    @Override
    public Object getCredentials() {
        return rawToken;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    public ApiToken getApiToken() {
        return token;
    }
}
