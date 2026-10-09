package ru.openapi.tokens.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "ru.openapi.tokens.persistence")
@EntityScan(basePackages = "ru.openapi.tokens.persistence.internal")
@EnableJpaRepositories(basePackages = "ru.openapi.tokens.persistence.internal")
public class OpenApiTokensPersistenceJpaConfig {
}
