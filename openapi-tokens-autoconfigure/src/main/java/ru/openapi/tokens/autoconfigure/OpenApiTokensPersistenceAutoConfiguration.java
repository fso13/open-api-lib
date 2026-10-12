package ru.openapi.tokens.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import ru.openapi.tokens.persistence.OpenApiTokensPersistenceJpaConfig;

import javax.sql.DataSource;

/**
 * Registers the JPA adapters. Ordering is explicit: the condition has to be evaluated after the
 * {@link DataSource} exists and before the main auto-configuration needs the repository beans.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = OpenApiTokensAutoConfiguration.class)
@ConditionalOnProperty(prefix = "openapi.tokens", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@ConditionalOnBean(DataSource.class)
@Import(OpenApiTokensPersistenceJpaConfig.class)
public class OpenApiTokensPersistenceAutoConfiguration {
}
