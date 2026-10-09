package ru.openapi.tokens.sample.ui;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the server-rendered UI (user + admin pages): session based form login with CSRF
 * protection on top of the shared {@link TokenUiSecurity} rules.
 *
 * <p>The REST API chains (starter {@code /api/openapi/**} and the sample {@code /api/demo/**} chain)
 * stay stateless and keep HTTP Basic / bearer token authentication.</p>
 */
@Configuration
public class UiSecurityConfig {

    @Bean
    @Order(TokenUiSecurity.UI_FILTER_CHAIN_ORDER)
    SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        return TokenUiSecurity.uiChain(http, "/login")
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .build();
    }
}
