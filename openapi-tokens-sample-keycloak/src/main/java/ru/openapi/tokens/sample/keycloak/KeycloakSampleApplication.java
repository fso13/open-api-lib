package ru.openapi.tokens.sample.keycloak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample application that authenticates users and API clients with Keycloak
 * ({@code openapi.tokens.auth.mode=keycloak}) and reuses the shared token UI.
 */
@SpringBootApplication(scanBasePackages = {
        "ru.openapi.tokens.sample.keycloak",
        "ru.openapi.tokens.sample.ui"
})
public class KeycloakSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeycloakSampleApplication.class, args);
    }
}
