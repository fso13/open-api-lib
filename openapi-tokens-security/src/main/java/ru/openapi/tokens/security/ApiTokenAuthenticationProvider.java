package ru.openapi.tokens.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.exception.InvalidTokenFormatException;
import ru.openapi.tokens.token.spi.ApiTokenAuthenticator;

import java.util.Objects;

/**
 * Spring {@link AuthenticationProvider} for API tokens.
 */
public final class ApiTokenAuthenticationProvider implements AuthenticationProvider {

    private final ApiTokenAuthenticator authenticator;

    public ApiTokenAuthenticationProvider(ApiTokenAuthenticator authenticator) {
        this.authenticator = Objects.requireNonNull(authenticator);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final String raw = Objects.toString(authentication.getCredentials(), null);
        try {
            final RawToken rawToken = RawToken.parse(raw);
            return authenticator.authenticate(rawToken)
                    .map(result -> ApiTokenAuthentication.authenticated(result.token(), result.authorities()))
                    .orElseThrow(() -> new BadCredentialsException("Invalid API token"));
        } catch (InvalidTokenFormatException ex) {
            throw new BadCredentialsException("Invalid API token format", ex);
        } catch (RateLimitExceededException ex) {
            throw ex;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
