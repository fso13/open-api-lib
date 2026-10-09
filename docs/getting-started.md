# Быстрый старт

Практическое руководство: от пустого проекта до работающего API-токена за несколько шагов.
Ориентировочное время — 15 минут.

## Требования

| Компонент | Версия | Проверка |
|---|---|---|
| JDK | 21+ | `java -version` |
| Gradle | 8.12.1 (wrapper в репозитории) | `./gradlew --version` |
| PostgreSQL | 16 (можно контейнером) | `docker compose up -d` |
| Docker | для тестов и локальной БД | `docker info` |

## Шаг 1. Подключить зависимость

Библиотека пока **не опубликована** в Maven-репозиторий, поэтому подключение — через
локальный проект или локальный репозиторий.

=== "includeBuild (рекомендуется)"

    ```kotlin
    // settings.gradle.kts приложения
    includeBuild("../open-api-lib")
    ```

    ```kotlin
    // build.gradle.kts приложения
    dependencies {
        implementation("ru.openapi:openapi-tokens-spring-boot-starter:0.1.0-SNAPSHOT")
    }
    ```

=== "publishToMavenLocal"

    ```bash
    # в репозитории библиотеки
    ./gradlew publishToMavenLocal   # требует настройки maven-publish (см. roadmap)
    ```

    ```kotlin
    repositories { mavenLocal() }
    dependencies {
        implementation("ru.openapi:openapi-tokens-spring-boot-starter:0.1.0-SNAPSHOT")
    }
    ```

!!! warning "`maven-publish` не настроен"
    В модулях библиотеки нет `maven-publish`/`publishing`, поэтому `publishToMavenLocal`
    сейчас не сработает. Используйте `includeBuild` или добавьте публикацию самостоятельно —
    см. [Ограничения и roadmap](operations/roadmap.md).

Starter тянет за собой `core`, `persistence-jpa`, `security`, `web` и `autoconfigure`.

## Шаг 2. Поднять PostgreSQL

```bash
cd open-api-lib
docker compose up -d
```

Создаётся база `openapi_tokens` с пользователем `openapi` / `openapi` на порту `5432`
(`docker-compose.yml`, образ `postgres:16-alpine`).

Проверка:

```bash
docker compose ps
psql postgresql://openapi:openapi@localhost:5432/openapi_tokens -c '\dt'
```

## Шаг 3. Минимальная конфигурация

```yaml
# application.yml приложения
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/openapi_tokens
    username: openapi
    password: openapi
  jpa:
    hibernate:
      ddl-auto: validate    # схему создаёт Flyway, Hibernate только проверяет
    open-in-view: false
  flyway:
    enabled: true           # применит V1__api_tokens.sql и V2__scope_catalog.sql

openapi:
  tokens:
    enabled: true
    auth:
      mode: internal        # владелец — из текущей аутентификации приложения
    scopes:
      mode: identity        # скоуп токена = право приложения
      catalog:
        "[payments:read]": "Чтение платежей"
        "[reports:read]": "Чтение отчётов"
```

!!! danger "`ddl-auto` не должен быть `update`/`create`"
    Схема принадлежит Flyway-миграциям библиотеки. Используйте `validate` (как в демо-приложении),
    иначе Hibernate и Flyway будут конфликтовать за владение схемой.

## Шаг 4. Обеспечить аутентификацию пользователей

Библиотека **не аутентифицирует людей** — она определяет владельца токена из уже
существующего `Authentication`. Поэтому в приложении должно быть настроено хоть что-то:
HTTP Basic, form login, Keycloak/OIDC.

Минимальный вариант (как в демо-приложении):

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("demo").password(encoder.encode("demo")).roles("USER").build(),
                User.withUsername("admin").password(encoder.encode("admin")).roles("ADMIN").build()
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain appSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
```

Цепочка безопасности статера для `/api/openapi/**` создаётся автоматически
(`@Order(1)`), а ваша цепочка покроет остальные пути.

!!! note "Готовая защита `/api/openapi/**`"
    Статер сам защищает свои эндпоинты: `/api/openapi/admin/**` требует `ROLE_ADMIN`,
    остальные — любой аутентификации, CSRF отключён, включён HTTP Basic.
    Переопределить — объявив свой бин `openapiTokensSecurityFilterChain`.

## Шаг 5. Запустить и выпустить первый токен

```bash
./gradlew bootRun
```

Приложение применит миграции Flyway и поднимет `/api/openapi/**`.

```bash
# создать токен (владелец demo)
curl -s -u demo:demo -H 'Content-Type: application/json' \
  -d '{"name":"ci-bot","scopes":["payments:read"]}' \
  http://localhost:8080/api/openapi/tokens | jq .
```

```json
{
  "token": {
    "id": "0f9a1b7c-1a2b-4c3d-8e4f-5a6b7c8d9e0f",
    "name": "ci-bot",
    "prefix": "9f3c1a7b",
    "ownerId": "demo",
    "status": "ACTIVE",
    "scopes": ["payments:read"],
    "createdAt": "2026-10-07T11:00:00Z"
  },
  "rawToken": "atk_9f3c1a7b_5d8e…c1"
}
```

## Шаг 6. Использовать токен

```bash
RAW=atk_9f3c1a7b_5d8e…c1

curl -H "Authorization: Bearer $RAW" http://localhost:8080/api/your-endpoint
```

В своём коде проверяйте права привычным способом:

```java
@GetMapping("/api/your-endpoint")
@PreAuthorize("hasAuthority('payments:read')")
public List<Payment> payments() { … }
```

Не забудьте `@EnableMethodSecurity` — без него `@PreAuthorize` не работает.

## Шаг 7. Запустить демо-приложение

Если нужно увидеть всё «в сборе» (REST + UI пользователя и администратора):

```bash
docker compose up -d
./gradlew :openapi-tokens-sample:bootRun
```

| Что | Где | Учётные данные |
|---|---|---|
| Форма входа | <http://localhost:8080/login> | `demo`/`demo` (USER), `admin`/`admin` (ADMIN) |
| Мои токены | <http://localhost:8080/ui/tokens> | demo |
| Создание токена | <http://localhost:8080/ui/tokens/new> | demo |
| Справочник скоупов | <http://localhost:8080/ui/scopes> | любой аутентифицированный |
| Админка | <http://localhost:8080/admin/tokens> | admin |
| Демо-API | `/api/demo/ping`, `/api/demo/payments` | Bearer-токен |

```bash
# проверить, что токен реально даёт доступ
TOKEN=$(curl -s -u demo:demo -H 'Content-Type: application/json' \
  -d '{"name":"ci","scopes":["payments:read"]}' \
  http://localhost:8080/api/openapi/tokens | jq -r .rawToken)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/demo/payments
```

Полезные страницы: `/` (редирект по роли), `/error`, `error/403`, `error/404`.

### Тот же UI с Keycloak (режим `keycloak`)

Второе демо-приложение показывает OIDC-вход через Keycloak и приём **двух** типов токенов
(Keycloak access token и API-токен) на одном API:

```bash
docker compose --profile keycloak up -d      # PostgreSQL 5433 + Keycloak 8180 (realm импортируется)
./gradlew :openapi-tokens-sample-keycloak:bootRun
```

| Что | Где | Учётные данные |
|---|---|---|
| Приложение (UI + REST) | <http://localhost:8081> | вход через Keycloak |
| Keycloak Admin Console | <http://localhost:8180> | `admin`/`admin` |
| Пользователи realm | — | `demo`/`demo` (USER), `admin`/`admin` (ADMIN) |
| Кто я (sub, tenant, authorities) | `/api/demo/whoami` | Keycloak JWT или API-токен |
| Только скоуп API-токена | `/api/demo/payments` | API-токен со скоупом `payments:read` |
| Только роль Keycloak | `/api/demo/reports` | Keycloak JWT с ролью `ADMIN` |

Подробно: [Keycloak: интеграция](guides/keycloak.md).

## Шаг 8. Прогнать тесты

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test
```

Тестам нужен работающий Docker: интеграционные тесты поднимают PostgreSQL 16 через
Testcontainers. Подробнее — [Тестирование](operations/testing.md).

---

## Чек-лист интеграции

- [ ] Добавлена зависимость `openapi-tokens-spring-boot-starter`.
- [ ] Настроены `spring.datasource.*`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`.
- [ ] Настроена аутентификация пользователей (Basic/form/Keycloak) и `@EnableMethodSecurity`.
- [ ] Определён режим скоупов (`identity` или `mapped`) и заполнен `scopes.catalog`.
- [ ] `scopes.catalog` использует квадратные скобки в ключах со `:`.
- [ ] Значение `limits.max-tokens-per-owner` соответствует бизнес-требованиям.
- [ ] **Ограничен набор допустимых скоупов** при создании токена (иначе возможно
      повышение привилегий — см. [Аутентификация](features/security.md#повышение-привилегий-через-скоупы)).
- [ ] Настроена ротация/уборка таблицы `api_token_audit_log`.
- [ ] Решено, нужен ли Redis-лимитер (в кластере in-memory даёт N× лимит).
- [ ] Проверен `openapi.tokens.auth.mode` (для Keycloak добавлена зависимость resource-server).
- [ ] Настроен мониторинг ошибок `Failed to append audit event for token …`.

## Типичные проблемы

| Симптом | Причина | Решение |
|---|---|---|
| Приложение не стартует: `Schema-validation: missing table [api_token]` | Flyway не выполнился | `spring.flyway.enabled=true`, проверить `spring.flyway.locations` |
| Приложение не стартует: `BindValidationException` | Некорректное свойство `openapi.tokens.*` | Проверить `@Min`/`@NotBlank`-ограничения в [Конфигурации](configuration.md#валидация-свойств) |
| `IllegalStateException: No authenticated principal for token owner resolution` | Нет аутентификации при вызове `/api/openapi/tokens` | Настроить Basic/form/Keycloak для этих путей |
| В справочнике скоупов «битые» имена (`payments` вместо `payments:read`) | Ключ без квадратных скобок | `"[payments:read]": "…"` |
| `POST /api/openapi/tokens` → 400 `prefixLength must be between 4 and 16` | `token.prefix-length` ≥ 17 | Значение 4…16 |
| `POST` → 409 `Token quota exceeded` | Достигнут лимит (учитываются и отозванные токены) | Поднять `limits.max-tokens-per-owner` |
| Статистика всегда нулевая | Нет бина `AuditLogRepository` (не подключена персистентность) | Проверить `DataSource` и `ddl-auto` |
| Запрос с токеном → 401, хотя токен новый | Токен просрочен или `BLOCKED`/`REVOKED` | Проверить `expiresAt`, `status`, `slidingTtlSeconds` |
| Запрос → 429 | Сработал rate limit | Уменьшить частоту или увеличить лимит при перевыпуске токена |
| CSS/статика UI отдаётся 401 | `PUBLIC_PATHS` не входит в `securityMatchers` UI-цепочки | Добавить `/css/**` в `WebSecurityCustomizer` — детали в [Web UI](guides/web-ui.md#7-известные-особенности) |

## Что дальше

| Задача | Раздел |
|---|---|
| Понять, что умеет библиотека | [Обзор функций](features/index.md) |
| Настроить скоупы и права | [Скоупы и права](features/scopes.md) |
| Настроить Keycloak | [Аутентификация](features/security.md) |
| Встроить UI или свой фронтенд | [Фронтенд-разработчику](guides/frontend.md) |
| Расширить/заменить компоненты | [Порты и SPI](api/spi.md) |
| Понять требования и правила | [Бизнес-аналитику](guides/business-analyst.md) |
