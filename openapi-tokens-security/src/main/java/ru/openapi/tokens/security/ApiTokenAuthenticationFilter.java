package ru.openapi.tokens.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.RawToken;
import ru.openapi.tokens.token.spi.AuditRecorder;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/**
 * Extracts {@code Authorization: Bearer atk_…} and authenticates via {@link AuthenticationManager}.
 */
public final class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final String headerName;
    private final String bearerPrefix;

    public ApiTokenAuthenticationFilter(
            AuthenticationManager authenticationManager,
            AuditRecorder auditRecorder,
            Clock clock,
            String headerName,
            String bearerPrefix
    ) {
        this.authenticationManager = Objects.requireNonNull(authenticationManager);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.clock = Objects.requireNonNull(clock);
        this.headerName = Objects.requireNonNull(headerName);
        this.bearerPrefix = Objects.requireNonNull(bearerPrefix);
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
        try {
            final var authentication = authenticationManager.authenticate(
                    ApiTokenAuthentication.unauthenticated(raw)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            recordAudit(request, authentication, true, HttpStatus.OK.value());
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException ex) {
            recordAudit(request, null, false, HttpStatus.TOO_MANY_REQUESTS.value());
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
        } catch (AuthenticationException ex) {
            recordAudit(request, null, false, HttpStatus.UNAUTHORIZED.value());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
        }
    }

    private void recordAudit(
            HttpServletRequest request,
            org.springframework.security.core.Authentication authentication,
            boolean success,
            int status
    ) {
        final var apiToken = authentication instanceof ApiTokenAuthentication apiAuth
                ? apiAuth.getApiToken()
                : null;
        auditRecorder.record(new AuditEvent(
                apiToken == null ? null : apiToken.id(),
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
