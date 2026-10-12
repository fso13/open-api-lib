package ru.openapi.tokens.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * Token creation request.
 *
 * <p>Unknown JSON properties are rejected (see {@link #rejectUnknownProperty}) so that typos such as
 * {@code expireAt} do not silently produce a token without expiry.</p>
 */
public record CreateTokenRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotEmpty @Size(max = 64) Set<@NotBlank @Size(max = 128) String> scopes,
        @Future Instant expiresAt,
        @Positive @Max(31_536_000) Integer slidingTtlSeconds,
        @Positive @Max(1_000_000) Integer rateLimitRequests,
        @Positive @Max(31_536_000) Integer rateLimitWindowSeconds,
        @Size(max = 128) String tenantId
) {

    /**
     * A rate limit must be complete: a single field would silently mean "unlimited".
     */
    @JsonIgnore
    @AssertTrue(message = "rateLimitRequests and rateLimitWindowSeconds must be provided together")
    public boolean isRateLimitConsistent() {
        return (rateLimitRequests == null) == (rateLimitWindowSeconds == null);
    }

    /**
     * Fails the request on any unknown JSON property (Jackson ignores them by default).
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property '%s'".formatted(name));
    }
}
