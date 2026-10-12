package ru.openapi.tokens.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.spi.AuditRecorder;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Extracts {@code Authorization: Bearer atk_…} and authenticates via {@link AuthenticationManager}.
 *
 * <p>Responses are rendered as {@code application/problem+json} with a {@code WWW-Authenticate}
 * challenge (401) and {@code Retry-After} (429) instead of the container error page. The
 * authentication audit event is recorded after the request has been handled so that the real
 * response status is stored.</p>
 */
public final class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final int DEFAULT_RETRY_AFTER_SECONDS = 60;

    private final AuthenticationManager authenticationManager;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final String headerName;
    private final String bearerPrefix;
    private final int retryAfterSeconds;
    private final boolean recordFailures;

    public ApiTokenAuthenticationFilter(
            AuthenticationManager authenticationManager,
            AuditRecorder auditRecorder,
            Clock clock,
            String headerName,
            String bearerPrefix
    ) {
        this(authenticationManager, auditRecorder, clock, headerName, bearerPrefix, DEFAULT_RETRY_AFTER_SECONDS, true);
    }

    public ApiTokenAuthenticationFilter(
            AuthenticationManager authenticationManager,
            AuditRecorder auditRecorder,
            Clock clock,
            String headerName,
            String bearerPrefix,
            int retryAfterSeconds
    ) {
        this(authenticationManager, auditRecorder, clock, headerName, bearerPrefix, retryAfterSeconds, true);
    }

    public ApiTokenAuthenticationFilter(
            AuthenticationManager authenticationManager,
            AuditRecorder auditRecorder,
            Clock clock,
            String headerName,
            String bearerPrefix,
            int retryAfterSeconds,
            boolean recordFailures
    ) {
        this.authenticationManager = Objects.requireNonNull(authenticationManager);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.clock = Objects.requireNonNull(clock);
        this.headerName = Objects.requireNonNull(headerName);
        this.bearerPrefix = Objects.requireNonNull(bearerPrefix);
        this.retryAfterSeconds = retryAfterSeconds > 0 ? retryAfterSeconds : DEFAULT_RETRY_AFTER_SECONDS;
        this.recordFailures = recordFailures;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String header = request.getHeader(headerName);
        if (header == null || !header.startsWith(bearerPrefix)) {
            filterChain.doFilter(request, response);
            return;
        }
        final String raw = header.substring(bearerPrefix.length()).trim();
        if (!raw.startsWith(RawToken.SCHEME + "_")) {
            filterChain.doFilter(request, response);
            return;
        }
        final Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    ApiTokenAuthentication.unauthenticated(raw)
            );
        } catch (RateLimitExceededException ex) {
            recordAudit(request, null, ex.getTokenId(), false, HttpStatus.TOO_MANY_REQUESTS.value());
            writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", true);
            return;
        } catch (AuthenticationException ex) {
            recordAudit(request, null, null, false, HttpStatus.UNAUTHORIZED.value());
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Unauthorized", false);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            recordAudit(request, authentication, null, true, response.getStatus());
        }
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String detail,
            boolean rateLimited
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        if (rateLimited) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.getWriter().write("""
                {"status":%d,"title":"%s","detail":"%s"}"""
                .formatted(status.value(), status.getReasonPhrase(), detail));
    }

    private void recordAudit(
            HttpServletRequest request,
            Authentication authentication,
            UUID fallbackTokenId,
            boolean success,
            int status
    ) {
        if (!success && !recordFailures) {
            return;
        }
        final var apiToken = authentication instanceof ApiTokenAuthentication apiAuth
                ? apiAuth.getApiToken()
                : null;
        final UUID tokenId = apiToken != null ? apiToken.id() : fallbackTokenId;
        auditRecorder.record(new AuditEvent(
                tokenId,
                apiToken == null ? null : apiToken.ownerId(),
                apiToken == null ? null : apiToken.tenantId(),
                request.getMethod(),
                request.getRequestURI(),
                "AUTHENTICATE",
                success,
                status,
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                clock.instant()
        ));
    }
}
