# Конфигурация

Все настройки библиотеки находятся под префиксом **`openapi.tokens`** и описываются классом
`OpenApiTokensProperties` (`@ConfigurationProperties`, `@Validated`). Поддерживаются
`application.yml`, `application.properties`, переменные окружения и профили —
по обычным правилам Spring Boot (relaxed binding).

## Полный справочник свойств

| Свойство | Тип | По умолчанию | Валидация | Влияет на |
|---|---|---|---|---|
| `openapi.tokens.enabled` | `boolean` | `true` | — | Главный переключатель: `false` — ни один бин статера не создаётся |
| `openapi.tokens.auth.mode` | `String` (`internal` \| `keycloak`) | `internal` | `@NotBlank` | Выбор `TokenOwnerResolver`: `internal` или `keycloak` |
| `openapi.tokens.auth.reject-empty-authorities` | `boolean` | `true` | — | Токен без единой authority не аутентифицируется |
| `openapi.tokens.auth.failed-attempt-limit` | `int` | `60` | `@Min(0)` | Пред-аутентификационная защита от перебора: число неудачных попыток до проверки хеша на ключ `auth-fail:<prefix>`. `0` — отключает |
| `openapi.tokens.auth.failed-attempt-window-seconds` | `int` | `60` | `@Min(1)` | Окно (секунды) для `failed-attempt-limit` |
| `openapi.tokens.auth.keycloak.tenant-claim` | `String` | `tenant_id` | `@NotBlank` | Имя claim с тенантом в режиме `keycloak` |
| `openapi.tokens.scopes.mode` | `enum` (`identity` \| `mapped`) | `identity` | `@NotNull`, значение — enum | `identity` (1:1) или `mapped` (1:M). Опечатка = **ошибка старта** (раньше молча трактовалась как `identity`) |
| `openapi.tokens.scopes.allow-tenant-override` | `boolean` | `false` | — | При `false` запрошенный `tenantId`, отличный от тенанта владельца, отклоняется с **403** |
| `openapi.tokens.scopes.validation.mode` | `enum` (`off` \| `deny-list` \| `catalog` \| `strict`) | `catalog` | `@NotNull`, значение — enum | Политика валидации скоупов при создании токена |
| `openapi.tokens.scopes.validation.deny-patterns` | `List<String>` | `["ROLE_.*"]` | — | Регулярные выражения скоупов, которые нельзя выдать |
| `openapi.tokens.scopes.validation.privileged-authorities` | `Set<String>` | `["ROLE_ADMIN"]` | — | Authority, которым разрешено делегировать любой скоуп (актуально для `strict`) |
| `openapi.tokens.scopes.mappings` | `Map<String, Set<String>>` | `{}` | — | Маппинги «скоуп токена → права приложения» |
| `openapi.tokens.scopes.catalog` | `Map<String, String>` | `{}` | — | Описания (и тем самым объявление) известных скоупов для справочника |
| `openapi.tokens.hashing.algorithm` | `enum` (`argon2` \| `bcrypt`) | `ARGON2` | `@NotNull` | Алгоритм для **новых** хешей. Смена безопасна: хеши хранятся с тегом `{argon2}`/`{bcrypt}` |
| `openapi.tokens.rate-limit.backend` | `String` | `memory` | `@NotBlank` | ⚠️ **по-прежнему не используется** |
| `openapi.tokens.rate-limit.retry-after-seconds` | `int` | `60` | `@Min(1)` `@Max(86400)` | Значение заголовка `Retry-After` в ответах **429** |
| `openapi.tokens.audit.enabled` | `boolean` | `true` | — | `false` → `NoOpAuditRecorder`: аудит реально выключается |
| `openapi.tokens.audit.async` | `boolean` | `true` | — | `false` → `SyncAuditRecorder`: запись в потоке запроса |
| `openapi.tokens.audit.record-failures` | `boolean` | `true` | — | `false` → неуспешные аутентификации в аудит не пишутся |
| `openapi.tokens.limits.max-tokens-per-owner` | `int` | `10` | `@Min(1)` | Квота на владельца; считаются `ACTIVE` + `BLOCKED`, отзыв освобождает слот |
| `openapi.tokens.token.prefix-length` | `int` | `8` | `@Min(4)` | Длина публичного префикса (фактически допустимо 4…16) |
| `openapi.tokens.token.default-ttl` | `Duration` | `90d` | `@NotNull` | TTL по умолчанию: применяется, если клиент не задал ни `expiresAt`, ни `slidingTtlSeconds` |
| `openapi.tokens.token.header` | `String` | `Authorization` | `@NotBlank` | Заголовок, из которого читается токен |
| `openapi.tokens.token.bearer-prefix` | `String` | `Bearer ` (с пробелом) | `@NotBlank` | Префикс схемы в заголовке |

!!! note "Единственное неиспользуемое свойство — `rate-limit.backend`"
    Все остальные свойства читаются кодом авто-конфигурации. `rate-limit.backend` оставлено
    для совместимости: библиотека всегда создаёт in-memory `Bucket4jRateLimiter`, а
    распределённый лимитер подключается своим бином `RateLimiter`
    (см. [Порты и SPI](api/spi.md)).

!!! tip "Что изменилось после усиления безопасности"
    `hashing.algorithm`, `token.default-ttl` и все `audit.*` теперь действительно влияют на
    поведение: смена алгоритма не инвалидирует выданные токены, `default-ttl` подставляется
    при создании, а аудит можно выключить или сделать синхронным.

## Базовый пример

```yaml
openapi:
  tokens:
    enabled: true
    auth:
      mode: internal                    # internal | keycloak
      reject-empty-authorities: true
      failed-attempt-limit: 60          # 0 — отключить пред-аутентификационный лимит
      failed-attempt-window-seconds: 60
    scopes:
      mode: identity                    # identity | mapped
      allow-tenant-override: false      # false → чужой tenantId отклоняется с 403
      validation:
        mode: catalog                   # off | deny-list | catalog | strict
        deny-patterns: ["ROLE_.*"]
        privileged-authorities: ["ROLE_ADMIN"]
      mappings:
        payment:
          - createPayment
          - readPayment
      catalog:
        # ключи со спецсимволами (':') — ТОЛЬКО в квадратных скобках
        "[payments:read]": "Чтение платежей"
        "[payments:write]": "Создание и изменение платежей"
    hashing:
      algorithm: argon2                 # argon2 | bcrypt
    rate-limit:
      retry-after-seconds: 60           # заголовок Retry-After в ответах 429
    audit:
      enabled: true
      async: true
      record-failures: true
    limits:
      max-tokens-per-owner: 10
    token:
      prefix-length: 8
      default-ttl: 90d
      header: Authorization
      bearer-prefix: "Bearer "
```

## Режимы валидации скоупов

Валидация запрошенных скоупов выполняется при создании токена **до** проверки квоты и
генерации секрета: при отказе ничего не сохраняется. Проверку выполняет бин `ScopeValidator`
(см. [Порты и SPI](api/spi.md)), который собирается из делегатов по режиму.

| `scopes.validation.mode` | Что проверяется |
|---|---|
| `off` | Ничего (`ScopeValidator.allowAll()`) — обратная совместимость |
| `deny-list` | Только `deny-patterns` |
| `catalog` (по умолчанию) | `deny-patterns` + каждый скоуп известен справочнику |
| `strict` | То же, что `catalog`, плюс «нельзя делегировать больше, чем есть у вас»: вызывающий обязан сам обладать каждой authority, которую получит новый токен |

```yaml
openapi:
  tokens:
    scopes:
      validation:
        mode: catalog
        deny-patterns: ["ROLE_.*"]
        privileged-authorities: ["ROLE_ADMIN"]
```

Отказ возвращает REST-ответ **422** (`Invalid Scopes`) с машинно-читаемыми полями `reason`
(`UNKNOWN_SCOPE` | `DENIED_SCOPE` | `NOT_GRANTABLE`) и `invalidScopes` — подробнее в
[REST API](api/rest-api.md#post-apiopenapitokens--создать-токен).

!!! danger "При `mode != off` скоупы должны быть объявлены"
    Если `scopes.validation.mode` не `off`, каждый запрошенный скоуп должен быть известен:
    описан в `scopes.catalog`, присутствовать в таблице `scope_catalog` или упоминаться в
    `scopes.mappings`. Иначе создание токена вернёт **422**, и выпустить токен не получится.

    В `strict` проверка идёт по **спроецированным** authority: в режиме `identity` — по самим
    скоупам, в `mapped` — по тому, что дают маппинги. Вызывающие с authority из
    `privileged-authorities` (по умолчанию `ROLE_ADMIN`) проверку проходят всегда.

## Примеры по сценариям

=== "Публичный API с простыми скоупами"

    ```yaml
    openapi:
      tokens:
        scopes:
          mode: identity
          validation:
            mode: catalog
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
          validation:
            mode: strict
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

    В `strict` вызывающий должен сам обладать `ORDER_READ`/`ORDER_WRITE`, чтобы выпустить
    токен со скоупом `orders` (кроме `ROLE_ADMIN`).

=== "Аудит: выключить или сделать синхронным"

    ```yaml
    openapi:
      tokens:
        audit:
          enabled: true
          async: false          # запись в потоке запроса (SyncAuditRecorder)
          record-failures: false
    ```

    `audit.enabled: false` создаёт `NoOpAuditRecorder` — события не пишутся вообще.

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
| `auth.failed-attempt-limit` | `@Min(0)` | Отрицательное — старт не состоится |
| `auth.failed-attempt-window-seconds` | `@Min(1)` | `0` — старт не состоится |
| `rate-limit.retry-after-seconds` | `@Min(1)` `@Max(86400)` | `0` или `100000` — старт не состоится |
| `auth.mode`, `rate-limit.backend`, `token.header`, `token.bearer-prefix`, `auth.keycloak.tenant-claim` | `@NotBlank` | Пустая строка — старт не состоится |
| `hashing.algorithm`, `token.default-ttl` | `@NotNull` | `null` — старт не состоится |
| `scopes.mode`, `scopes.validation.mode` | значение — enum | Опечатка (`identiy`, `catlog`, `denylist`) — старт не состоится |

!!! danger "Опечатка в enum-свойстве = ошибка старта"
    `scopes.mode` и `scopes.validation.mode` имеют **тип-enum**, поэтому неизвестное значение
    (`identiy`, `catlog`, `deny_list`) Spring Boot не «проигнорирует»: приложение не стартует
    с ошибкой привязки свойств. Допустимые значения: `identity` | `mapped` и
    `off` | `deny-list` | `catalog` | `strict`.

```yaml
# ✅ правильно
openapi.tokens.scopes.validation.mode: deny-list

# ❌ неправильно — старт упадёт
openapi.tokens.scopes.validation.mode: deny_list
```

!!! bug "Верхняя граница `prefix-length` не проверяется свойством"
    Свойство допускает `prefix-length: 17` и больше (`@Min(4)` без верхней границы), но
    `RawToken.generate` бросает `IllegalArgumentException("prefixLength must be between 4 and 16")`
    при создании токена → HTTP **400** на `POST /api/openapi/tokens`. Держите значение в 4…16.

## Значения по умолчанию, если ничего не указывать

```yaml
openapi:
  tokens:
    enabled: true
    auth:
      mode: internal
      reject-empty-authorities: true
      failed-attempt-limit: 60
      failed-attempt-window-seconds: 60
      keycloak: { tenant-claim: tenant_id }
    scopes:
      mode: identity
      allow-tenant-override: false
      validation: { mode: catalog, deny-patterns: ["ROLE_.*"], privileged-authorities: ["ROLE_ADMIN"] }
      mappings: {}
      catalog: {}
    hashing: { algorithm: argon2 }
    rate-limit: { backend: memory, retry-after-seconds: 60 }   # backend не используется
    audit: { enabled: true, async: true, record-failures: true }
    limits: { max-tokens-per-owner: 10 }
    token:
      prefix-length: 8
      default-ttl: 90d
      header: Authorization
      bearer-prefix: "Bearer "
```

Библиотека полностью работоспособна без единой строки конфигурации, **если** в приложении есть
`DataSource` и JPA (для персистентности) и настроена аутентификация пользователей
(для определения владельца). Но помните: при `scopes.validation.mode: catalog` по умолчанию
пустой `scopes.catalog` означает, что **ни один** скоуп не известен — создание токена вернёт
**422**, пока скоупы не объявлены в `scopes.catalog`, таблице `scope_catalog` или `scopes.mappings`.

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
- [Аудит и статистика](features/audit.md) — как читать журнал и статистику.
- [Порты и SPI](api/spi.md) — как заменить компонент, а не настраивать его.
