# Конфигурация

Все настройки библиотеки находятся под префиксом **`openapi.tokens`** и описываются классом
`OpenApiTokensProperties` (`@ConfigurationProperties`, `@Validated`). Поддерживаются
`application.yml`, `application.properties`, переменные окружения и профили —
по обычным правилам Spring Boot (relaxed binding).

## Полный справочник свойств

| Свойство | Тип | По умолчанию | Валидация | Влияет на |
|---|---|---|---|---|
| `openapi.tokens.enabled` | `boolean` | `true` | — | Главный переключатель: `false` — ни один бин статера не создаётся |
| `openapi.tokens.auth.mode` | `String` | `internal` | `@NotBlank` | Выбор `TokenOwnerResolver`: `internal` или `keycloak` |
| `openapi.tokens.auth.keycloak.tenant-claim` | `String` | `tenant_id` | `@NotBlank` | Имя claim с тенантом в режиме `keycloak` |
| `openapi.tokens.scopes.mode` | `String` | `identity` | `@NotBlank` | `identity` (1:1) или `mapped` (1:M). Сравнение без учёта регистра; любое иное значение = `identity` |
| `openapi.tokens.scopes.mappings` | `Map<String, Set<String>>` | `{}` | — | Маппинги «скоуп токена → права приложения» |
| `openapi.tokens.scopes.catalog` | `Map<String, String>` | `{}` | — | Описания скоупов для справочника |
| `openapi.tokens.hashing.algorithm` | `enum` | `ARGON2` | `@NotNull` | `argon2` \| `bcrypt` |
| `openapi.tokens.rate-limit.backend` | `String` | `memory` | `@NotBlank` | ⚠️ **не используется** |
| `openapi.tokens.audit.enabled` | `boolean` | `true` | — | ⚠️ **не используется** |
| `openapi.tokens.audit.async` | `boolean` | `true` | — | ⚠️ **не используется** |
| `openapi.tokens.audit.record-failures` | `boolean` | `true` | — | ⚠️ **не используется** |
| `openapi.tokens.limits.max-tokens-per-owner` | `int` | `10` | `@Min(1)` | Квота токенов на владельца |
| `openapi.tokens.token.prefix-length` | `int` | `8` | `@Min(4)` | Длина публичного префикса (фактически допустимо 4…16) |
| `openapi.tokens.token.default-ttl` | `Duration` | `90d` | `@NotNull` | ⚠️ **не используется** |
| `openapi.tokens.token.header` | `String` | `Authorization` | `@NotBlank` | Заголовок, из которого читается токен |
| `openapi.tokens.token.bearer-prefix` | `String` | `Bearer ` (с пробелом) | `@NotBlank` | Префикс схемы в заголовке |

!!! danger "Четыре свойства объявлены, но не читаются кодом"
    | Свойство | Фактическое поведение |
    |---|---|
    | `rate-limit.backend` | Всегда используется in-memory `Bucket4jRateLimiter`; значение игнорируется |
    | `audit.enabled` | Аудит включён всегда; отключить можно только своим бином `AuditRecorder` |
    | `audit.async` | Запись всегда асинхронная |
    | `audit.record-failures` | Неуспешные попытки записываются всегда |
    | `token.default-ttl` | TTL по умолчанию не подставляется: используются только `expiresAt`/`slidingTtlSeconds` из запроса |

    Наличие этих свойств создаёт ложное впечатление управляемости. Если они нужны в работе —
    потребуется доработка библиотеки (см. [Ограничения и roadmap](operations/roadmap.md)).

## Базовый пример

```yaml
openapi:
  tokens:
    enabled: true
    auth:
      mode: internal          # internal | keycloak
    scopes:
      mode: identity          # identity | mapped
      mappings:
        payment:
          - createPayment
          - readPayment
      catalog:
        # ключи со спецсимволами (':') — ТОЛЬКО в квадратных скобках
        "[payment:read]": "Чтение платежей"
        "[payment:write]": "Создание и изменение платежей"
    hashing:
      algorithm: argon2       # argon2 | bcrypt
    limits:
      max-tokens-per-owner: 10
    token:
      prefix-length: 8
      default-ttl: 90d
      header: Authorization
      bearer-prefix: "Bearer "
```

## Примеры по сценариям

=== "Публичный API с простыми скоупами"

    ```yaml
    openapi:
      tokens:
        scopes:
          mode: identity
          catalog:
            "[orders:read]": "Чтение заказов"
            "[orders:write]": "Создание и отмена заказов"
        limits:
          max-tokens-per-owner: 5
        hashing:
          algorithm: argon2
    ```

=== "Внутренние права отличаются от внешних"

    ```yaml
    openapi:
      tokens:
        scopes:
          mode: mapped
          mappings:
            orders:
              - ORDER_READ
              - ORDER_WRITE
            reports:
              - REPORT_VIEW
          catalog:
            "[orders]": "Полный доступ к заказам"
            "[reports]": "Просмотр отчётов"
    ```

=== "Keycloak"

    ```yaml
    openapi:
      tokens:
        auth:
          mode: keycloak
          keycloak:
            tenant-claim: tenant_id   # или, например, org_id
    ```

    Требуется `spring-boot-starter-oauth2-resource-server` на classpath
    (или `oauth2-client`, если используется `oauth2Login`).

=== "Полное отключение"

    ```yaml
    openapi:
      tokens:
        enabled: false
    ```

    Не создаются: `Clock`, `TokenHasher`, `RateLimiter`, `ScopeResolver`, `ScopeCatalog`,
    `TokenOwnerResolver`, `AuditRecorder`, `ApiTokenService`, `ApiTokenAuthenticator`,
    контроллеры, обработчик ошибок, фильтр и `SecurityFilterChain` статера.
    Проверено тестом `OpenApiTokensAutoConfigurationTest`.

=== "Свой источник времени (тесты/детерминизм)"

    ```java
    @TestConfiguration
    class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        }
    }
    ```

    Бин `openApiTokensClock` создаётся только при отсутствии своего `Clock`.

## Ключи со спецсимволами в YAML

!!! danger "Скоупы с `:` пишутся в квадратных скобках"
    ```yaml
    # ✅ правильно
    "[payments:read]": "Чтение платежей"

    # ❌ неправильно — Spring потеряет часть ключа после ':'
    payments:read: "Чтение платежей"
    ```

    То же правило касается ключей `scopes.mappings`, если имя скоупа содержит `:`, `.`, `[`, `]`
    или другие специальные символы YAML/Spring. Для вложенных маппингов удобнее
    «плоская» запись:

    ```yaml
    openapi:
      tokens:
        scopes:
          mappings:
            "[payment:read]":
              - readPayment
    ```

## Валидация свойств

`OpenApiTokensProperties` помечен `@Validated`, поэтому некорректные значения приводят
к **падению старта приложения** (`ConfigurationPropertiesBindException` /
`BindValidationException`):

| Свойство | Ограничение | Пример ошибки |
|---|---|---|
| `limits.max-tokens-per-owner` | `@Min(1)` | `0` или отрицательное — старт не состоится |
| `token.prefix-length` | `@Min(4)` | `3` — старт не состоится |
| `auth.mode`, `scopes.mode`, `rate-limit.backend`, `token.header`, `token.bearer-prefix`, `auth.keycloak.tenant-claim` | `@NotBlank` | Пустая строка — старт не состоится |
| `hashing.algorithm`, `token.default-ttl` | `@NotNull` | `null` — старт не состоится |

!!! bug "Верхняя граница `prefix-length` не проверяется свойством"
    Свойство допускает `prefix-length: 17` и больше (`@Min(4)` без верхней границы), но
    `RawToken.generate` бросает `IllegalArgumentException("prefixLength must be between 4 and 16")`
    при создании токена → HTTP **400** на `POST /api/openapi/tokens`. Держите значение в 4…16.

## Значения по умолчанию, если ничего не указывать

```yaml
openapi:
  tokens:
    enabled: true
    auth: { mode: internal, keycloak: { tenant-claim: tenant_id } }
    scopes: { mode: identity, mappings: {}, catalog: {} }
    hashing: { algorithm: argon2 }
    rate-limit: { backend: memory }        # не используется
    audit: { enabled: true, async: true, record-failures: true }   # не используются
    limits: { max-tokens-per-owner: 10 }
    token:
      prefix-length: 8
      default-ttl: 90d                     # не используется
      header: Authorization
      bearer-prefix: "Bearer "
```

Библиотека полностью работоспособна без единой строки конфигурации, **если** в приложении есть
`DataSource` и JPA (для персистентности) и настроена аутентификация пользователей
(для определения владельца).

## Конфигурация инфраструктуры (не `openapi.tokens`)

Эти настройки на стороне приложения влияют на работу библиотеки:

| Свойство | Значение в демо | Почему важно |
|---|---|---|
| `spring.datasource.*` | PostgreSQL `localhost:5432/openapi_tokens`, `openapi`/`openapi` | Библиотека не создаёт `DataSource` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Схема должна совпадать с миграциями; иначе старт упадёт |
| `spring.jpa.open-in-view` | `false` | Ленивая загрузка скоупов требует транзакции — адаптеры её обеспечивают |
| `spring.flyway.enabled` | `true` | Миграции библиотеки должны примениться |
| `spring.flyway.table` | (не задано → `flyway_schema_history`) | Изоляция истории миграций, если она конфликтует с продуктом |
| `server.port` | `8080` | — |

Подробнее о схеме — [Модель данных](architecture/data-model.md).

## Связанные разделы

- [Быстрый старт](getting-started.md) — минимальный рабочий набор.
- [Скоупы и права](features/scopes.md) — семантика `scopes.*`.
- [Аутентификация](features/security.md) — семантика `auth.*` и `token.*`.
- [Аудит и статистика](features/audit.md) — почему `audit.*` не работает.
- [Порты и SPI](api/spi.md) — как заменить компонент, а не настраивать его.
