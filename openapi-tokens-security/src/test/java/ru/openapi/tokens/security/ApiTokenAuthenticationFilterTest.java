package ru.openapi.tokens.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.TokenCredentials;
import ru.openapi.tokens.token.spi.AuditRecorder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiTokenAuthenticationFilter")
class ApiTokenAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Mock
    private AuditRecorder auditRecorder;

    private ApiTokenAuthenticationFilter filter;
    private ApiToken token;

    @BeforeEach
    void setUp() {
        filter = new ApiTokenAuthenticationFilter(
                authenticationManager, auditRecorder, Clock.fixed(NOW, ZoneOffset.UTC),
                "Authorization", "Bearer ", 30
        );
        token = ApiToken.builder()
                .id(UUID.randomUUID())
                .name("ci")
                .credentials(new TokenCredentials("abcd1234", "hash"))
                .ownerId("owner-1")
                .tenantId("tenant-a")
                .scopes(Set.of("read"))
                .createdAt(NOW.minusSeconds(60))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String authorization) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo/payments");
        request.setRemoteAddr("10.0.0.1");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    @DisplayName("Should pass through without an Authorization header")
    void shouldPassThroughWithoutHeader() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(null), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(auditRecorder, never()).record(any());
    }

    @Test
    @DisplayName("Should pass through when the header is not an API token")
    void shouldPassThroughForeignHeader() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("Bearer some-jwt"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("Should authenticate, populate the context and audit the real status")
    void shouldAuthenticateAndAudit() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenReturn(ApiTokenAuthentication.authenticated(token, Set.of("payments:read")));
        final MockHttpServletRequest request = request("Bearer atk_abcd1234_secret");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(ApiTokenAuthentication.class);
        assertThat(chain.getRequest()).isNotNull();

        final ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(event.capture());
        assertThat(event.getValue().success()).isTrue();
        assertThat(event.getValue().tokenId()).isEqualTo(token.id());
        assertThat(event.getValue().responseStatus()).isEqualTo(200);
        assertThat(event.getValue().ip()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Should answer 401 as problem+json with a Bearer challenge")
    void shouldAnswerUnauthorized() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer atk_abcd1234_secret"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("\"status\":401");

        final ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(event.capture());
        assertThat(event.getValue().success()).isFalse();
        assertThat(event.getValue().responseStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should answer 429 with Retry-After and keep the token id in the audit")
    void shouldAnswerTooManyRequests() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new RateLimitExceededException(token.id()));
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer atk_abcd1234_secret"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");

        final ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(event.capture());
        assertThat(event.getValue().tokenId()).isEqualTo(token.id());
        assertThat(event.getValue().responseStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Should authenticate only once when invoked again inside the same request")
    void shouldBehaveAsOncePerRequestFilter() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenReturn(ApiTokenAuthentication.authenticated(token, Set.of("payments:read")));
        final MockHttpServletRequest request = request("Bearer atk_abcd1234_secret");

        // Nested invocation models the filter being registered both globally and inside a chain.
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> filter.doFilter(req, res, new MockFilterChain()));

        verify(authenticationManager, org.mockito.Mockito.times(1)).authenticate(any());
    }

    @Test
    @DisplayName("Should expose the token for programmatic checks")
    void shouldExposeApiToken() {
        final ApiTokenAuthentication authentication =
                ApiTokenAuthentication.authenticated(token, Set.of("payments:read"));
        assertThat(authentication.getApiToken()).isEqualTo(token);
        assertThat(authentication.getPrincipal()).isEqualTo(token);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(ApiTokenAuthentication.unauthenticated("raw").isAuthenticated()).isFalse();
        assertThat(Optional.ofNullable(ApiTokenAuthentication.unauthenticated("raw").getCredentials()))
                .contains("raw");
    }
}
