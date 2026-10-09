package ru.openapi.tokens.persistence;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(OpenApiTokensPersistenceJpaConfig.class)
class PersistenceTestApplication {
}
