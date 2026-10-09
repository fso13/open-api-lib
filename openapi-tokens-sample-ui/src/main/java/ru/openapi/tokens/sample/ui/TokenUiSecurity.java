package ru.openapi.tokens.sample.ui;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared security rules for the token UI: which URLs belong to the UI, which are public and which
 * need {@code ROLE_ADMIN}. The authentication mechanism itself (form login, Keycloak OIDC login, …)
 * is configured by the application on top of the returned {@link HttpSecurity}.
 */
public final class TokenUiSecurity {

    /**
     * Filter chain order right after the starter's {@code /api/openapi/**} chain (order 1).
     */
    public static final int UI_FILTER_CHAIN_ORDER = 2;

    /** URLs served by the shared UI. */
    public static final String[] UI_PATHS = {"/", "/ui/**", "/admin/**", "/logout"};

    /** URLs always reachable without authentication. */
    public static final String[] PUBLIC_PATHS = {"/css/**", "/error"};

    private TokenUiSecurity() {
    }

    /**
     * Applies the common UI rules: path matchers, authorization ({@code /admin/**} → {@code ROLE_ADMIN},
     * everything else authenticated) and session policy.
     *
     * @param http        the chain being built
     * @param loginPaths  additional public paths of the chosen login mechanism
     *                    ({@code /login} for form login, {@code /oauth2/**}, {@code /login/oauth2/**}
     *                    for Keycloak OIDC login)
     */
    public static HttpSecurity uiChain(HttpSecurity http, String... loginPaths) throws Exception {
        final String[] publicPaths = concat(PUBLIC_PATHS, loginPaths);
        return http
                .securityMatchers(matchers -> matchers.requestMatchers(concat(UI_PATHS, publicPaths)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
    }

    private static String[] concat(String[] first, String[] second) {
        final List<String> all = new ArrayList<>(first.length + second.length);
        all.addAll(List.of(first));
        all.addAll(List.of(second));
        return all.toArray(String[]::new);
    }
}
