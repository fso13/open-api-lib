package ru.openapi.tokens.sample.ui;

import ru.openapi.tokens.token.ApiToken;

import java.util.List;

/**
 * Status counters for the dashboard cards on the list pages.
 */
public record TokenCounters(long total, long active, long blocked, long revoked, long expired) {

    public static TokenCounters of(List<ApiToken> tokens) {
        long active = 0;
        long blocked = 0;
        long revoked = 0;
        long expired = 0;
        for (ApiToken token : tokens) {
            switch (token.status()) {
                case ACTIVE -> active++;
                case BLOCKED -> blocked++;
                case REVOKED -> revoked++;
                case EXPIRED -> expired++;
            }
        }
        return new TokenCounters(tokens.size(), active, blocked, revoked, expired);
    }
}
