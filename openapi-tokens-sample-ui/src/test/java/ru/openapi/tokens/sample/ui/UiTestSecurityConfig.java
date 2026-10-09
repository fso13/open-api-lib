package ru.openapi.tokens.sample.ui;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security chain for UI slices: shared {@link TokenUiSecurity} rules plus form login, so the
 * tests do not depend on the authentication mechanism of a concrete sample application.
 */
@TestConfiguration
public class UiTestSecurityConfig {

    @Bean
    @Order(TokenUiSecurity.UI_FILTER_CHAIN_ORDER)
    SecurityFilterChain testUiSecurityFilterChain(HttpSecurity http) throws Exception {
        return TokenUiSecurity.uiChain(http, "/login")
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    /**
     * Stand-in for the application's catch-all API chain: it makes sure the UI chain really owns the
     * paths it advertises (a non-matching {@code /css/**} would end up authenticated here).
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain testFallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
