package ru.openapi.tokens.sample.ui;

import org.springframework.stereotype.Component;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.AuditEvent;
import ru.openapi.tokens.token.RateLimitPolicy;
import ru.openapi.tokens.token.ScopeInfo;
import ru.openapi.tokens.token.TokenExpiry;
import ru.openapi.tokens.token.TokenStatus;
import ru.openapi.tokens.token.TokenUsageStats;
import ru.openapi.tokens.token.command.CreateTokenCommand;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Maps between domain objects / commands (tokens and the scope catalog) and the display-ready UI view models.
 * Presentation concerns (labels, CSS classes, date/time and duration formatting)
 * stay here, never in templates or controllers.
 */
@Component
public class TokenUiMapper {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Duration EXPIRING_SOON = Duration.ofDays(3);

    private final Clock clock;

    public TokenUiMapper(Clock clock) {
        this.clock = clock;
    }

    public CreateTokenCommand toCommand(CreateTokenForm form) {
        final boolean rateLimitGiven = form.rateLimitRequests() != null && form.rateLimitWindowSeconds() != null;
        return new CreateTokenCommand(
                form.name() == null ? null : form.name().trim(),
                blankToNull(form.description()),
                parseScopes(form.scopes()),
                parseExpiry(form.expiresAt()),
                form.slidingTtlSeconds() == null ? null : Duration.ofSeconds(form.slidingTtlSeconds()),
                rateLimitGiven
                        ? new RateLimitPolicy(form.rateLimitRequests(), form.rateLimitWindowSeconds())
                        : null,
                blankToNull(form.tenantId())
        );
    }

    /**
     * Parses the value of a {@code datetime-local} form field ({@code 2026-10-08T14:04}) into an
     * {@link Instant} in the application clock zone. {@code null}/blank means "no absolute expiry".
     *
     * @throws java.time.format.DateTimeParseException if the value is not an ISO local date-time
     */
    public Instant parseExpiry(String localDateTime) {
        if (localDateTime == null || localDateTime.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(localDateTime.trim()).atZone(clock.getZone()).toInstant();
    }

    /**
     * Splits a free text scope list on commas, whitespace and newlines.
     */
    public static Set<String> parseScopes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<TokenView> toViews(List<ApiToken> tokens) {
        return tokens.stream().map(this::toView).toList();
    }

    public List<ScopeView> toScopeViews(List<ScopeInfo> scopes) {
        return scopes.stream().map(this::toScopeView).toList();
    }

    public ScopeView toScopeView(ScopeInfo scope) {
        return new ScopeView(
                scope.scope(),
                scope.description(),
                scope.projectScopes().isEmpty() ? null : String.join(", ", scope.projectScopes())
        );
    }

    public TokenView toView(ApiToken token) {
        final TokenStatus status = token.status();
        return new TokenView(
                token.id(),
                token.name(),
                token.description(),
                token.credentials().prefix(),
                token.ownerId(),
                token.tenantId(),
                status.name(),
                statusLabel(status),
                statusCss(status),
                String.join(", ", token.scopes()),
                format(token.createdAt()),
                token.expiry().expiresAt() == null ? "бессрочно" : format(token.expiry().expiresAt()),
                isExpiringSoon(token),
                token.expiry().lastUsedAt() == null ? "не использовался" : format(token.expiry().lastUsedAt()),
                format(token.revokedAt()),
                slidingTtlLabel(token.expiry()),
                rateLimitLabel(token.rateLimit())
        );
    }

    public TokenUsageView toUsage(TokenUsageStats stats) {
        final long total = stats.totalRequests();
        final long failed = stats.failedRequests();
        return new TokenUsageView(
                stats.tokenId(),
                total,
                stats.successfulRequests(),
                failed,
                total == 0 ? "—" : Math.round(failed * 100.0 / total) + " %",
                stats.lastUsedAt() == null ? "не использовался" : format(stats.lastUsedAt())
        );
    }

    public List<AuditEntryView> toAuditViews(List<AuditEvent> events) {
        return events.stream().map(this::toAuditView).toList();
    }

    public AuditEntryView toAuditView(AuditEvent event) {
        final StringJoiner request = new StringJoiner(" ");
        if (event.httpMethod() != null) {
            request.add(event.httpMethod());
        }
        if (event.endpoint() != null) {
            request.add(event.endpoint());
        }
        return new AuditEntryView(
                format(event.createdAt()),
                event.action(),
                event.success(),
                event.success() ? "Успешно" : "Отклонено",
                request.toString(),
                event.responseStatus(),
                event.ip(),
                event.userAgent()
        );
    }

    /**
     * Options of the status filter: "all" plus every known status with its Russian label.
     */
    public static List<StatusOption> statusOptions() {
        final List<StatusOption> options = new ArrayList<>();
        options.add(new StatusOption(TokenFilter.ANY_STATUS, "Все статусы"));
        for (TokenStatus status : TokenStatus.values()) {
            options.add(new StatusOption(status.name(), statusLabel(status)));
        }
        return List.copyOf(options);
    }

    private static String statusLabel(TokenStatus status) {        return switch (status) {
            case ACTIVE -> "Активен";
            case BLOCKED -> "Заблокирован";
            case REVOKED -> "Отозван";
            case EXPIRED -> "Истёк";
        };
    }

    private static String statusCss(TokenStatus status) {
        return switch (status) {
            case ACTIVE -> "ok";
            case BLOCKED -> "warn";
            case REVOKED -> "danger";
            case EXPIRED -> "muted";
        };
    }

    private boolean isExpiringSoon(ApiToken token) {
        final Instant expiresAt = token.expiry().expiresAt();
        if (expiresAt == null || token.status() != TokenStatus.ACTIVE) {
            return false;
        }
        final Instant now = clock.instant();
        return !expiresAt.isBefore(now) && expiresAt.isBefore(now.plus(EXPIRING_SOON));
    }

    private String slidingTtlLabel(TokenExpiry expiry) {
        final Integer seconds = expiry.slidingTtlSeconds();
        return seconds == null ? "выключен" : humanSeconds(seconds);
    }

    private String rateLimitLabel(RateLimitPolicy policy) {
        if (policy == null || !policy.isConfigured()) {
            return "не ограничен";
        }
        return policy.requests() + " зап. / " + humanSeconds(policy.windowSeconds());
    }

    private static String humanSeconds(long seconds) {
        if (seconds % 86400 == 0) {
            return (seconds / 86400) + " сут";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + " ч";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + " мин";
        }
        return seconds + " сек";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String format(Instant instant) {
        return instant == null ? null : DATE_TIME.format(instant.atZone(clock.getZone()));
    }
}
