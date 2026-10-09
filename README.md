# open-api-lib

Reusable **Spring Boot Starter** for API tokens (GitHub PAT-style): create/revoke, scopes (1:1 / 1:M), TTL (absolute + sliding), rate limit, audit, Spring Security integration, optional Keycloak owner resolution.

## Requirements

- Java 21+
- Spring Boot 3.4+
- PostgreSQL (Flyway migrations in `openapi-tokens-persistence-jpa`)

## Modules

| Module | Role |
|--------|------|
| `openapi-tokens-core` | Domain, ports, hasher, scope resolvers, Bucket4j rate limiter, service |
| `openapi-tokens-persistence-jpa` | JPA adapters + Flyway (`V1__api_tokens.sql`, `V2__scope_catalog.sql`) |
| `openapi-tokens-security` | Filter, AuthenticationProvider, owner resolvers, permission checker |
| `openapi-tokens-web` | User + admin REST (`/api/openapi/tokens`, `/api/openapi/admin/tokens`, `/api/openapi/scopes`) |
| `openapi-tokens-autoconfigure` | AutoConfiguration + `OpenApiTokensProperties` |
| `openapi-tokens-spring-boot-starter` | Dependency aggregator |
| `openapi-tokens-sample` | Sample app: REST + web UI, `internal` auth mode |
| `openapi-tokens-sample-ui` | Shared Thymeleaf UI (user + admin + scope reference) used by the samples |
| `openapi-tokens-sample-keycloak` | Sample app: Keycloak OIDC login, JWT + API tokens, same UI |

## Add dependency

```kotlin
implementation("ru.openapi:openapi-tokens-spring-boot-starter:0.1.0-SNAPSHOT")
```

## Minimal configuration

```yaml
openapi:
  tokens:
    enabled: true          # set false to disable all starter beans
    auth:
      mode: internal       # internal | keycloak
      keycloak:
        tenant-claim: tenant_id   # claim with the tenant id (owner id is always `sub`)
    scopes:
      mode: identity       # identity | mapped
      mappings:
        payment:
          - createPayment
          - readPayment
      catalog:             # scope -> description, served by GET /api/openapi/scopes
        # ключи со спецсимволами (':') в YAML — только в квадратных скобках,
        # иначе Spring теряет ':' при биндинге Map<String, String>
        "[payment:read]": "Чтение платежей"
        "[payment:write]": "Создание и изменение платежей"
    hashing:
      algorithm: argon2    # argon2 | bcrypt
    rate-limit:
      backend: memory
    audit:
      enabled: true
      async: true
    limits:
      max-tokens-per-owner: 10
    token:
      prefix-length: 8
      default-ttl: 90d
```

## Create token (example)

`POST /api/openapi/tokens`

```json
{
  "name": "ci-bot",
  "scopes": ["payments:read"],
  "slidingTtlSeconds": 3600
}
```

Response (raw shown **once**):

```json
{
  "token": { "id": "...", "prefix": "abcd1234", "status": "ACTIVE", "...": "..." },
  "rawToken": "atk_abcd1234_<secret>"
}
```

Use as: `Authorization: Bearer atk_abcd1234_<secret>`.

## Security snippet

Starter registers `ApiTokenAuthenticationFilter` for `/api/openapi/**`. Product apps can also rely on resolved authorities with `@PreAuthorize("hasAuthority('createPayment')")`.

Auth modes:

- `internal` — `InternalTokenOwnerResolver` (current `Authentication` / `UserDetails`)
- `keycloak` — `KeycloakTokenOwnerResolver`: owner id = `sub`, tenant = `openapi.tokens.auth.keycloak.tenant-claim`.
  Works for resource-server JWTs (`JwtAuthenticationToken`) **and** for interactive OIDC logins
  (`OidcUser` principal from `oauth2Login`), so the same resolver serves the REST API and the web UI.

## Extension points

Provide your own Spring beans to override defaults (`@ConditionalOnMissingBean`):

| Port / type | Default |
|-------------|---------|
| `TokenHasher` | Argon2id / BCrypt via `openapi.tokens.hashing.algorithm` |
| `RateLimiter` | `Bucket4jRateLimiter` (in-memory; Redis later) |
| `ScopeResolver` | `IdentityScopeResolver` or `MappedScopeResolver` |
| `ScopeCatalog` | `DefaultScopeCatalog` (config + `scope_catalog` table) |
| `ScopeCatalogRepository` | JPA adapter |
| `TokenOwnerResolver` | Internal or Keycloak |
| `AuditRecorder` | `AsyncAuditRecorder` |
| `ApiTokenRepository` | JPA adapter |

## Scope catalog (scopes + descriptions)

`ScopeCatalog` is the single read API for "which scopes exist and what do they mean" — useful for
token creation UIs, documentation pages and admin consoles.

```java
@Autowired ScopeCatalog scopeCatalog;

List<ScopeInfo> scopes = scopeCatalog.listScopes();
// ScopeInfo(scope, description, projectScopes)
// -> ScopeInfo("payment:read", "Чтение платежей", Set.of("readPayment"))
```

Descriptors (in order of precedence):

1. **Table `scope_catalog`** (`scope` PK, `description`) — migration `V2__scope_catalog.sql`, wins over config;
2. **`openapi.tokens.scopes.catalog`** property (`scope → description`). В `application.yml`
   ключи со спецсимволами пишутся в квадратных скобках: `"[payment:read]": "Чтение платежей"`
   (иначе Spring Boot отбрасывает `:` при биндинге `Map<String, String>`);
3. `projectScopes` are resolved with the configured/stored mappings (`ScopeMappingSource`), which is
   empty in `identity` mode.

The returned list is the union of described scopes and scopes known from mappings, sorted by name.
Scopes without a description are returned with `description == null`.

| Access path | Where |
|---|---|
| Java | bean `ScopeCatalog#listScopes()` (override with your own bean) |
| REST | `GET /api/openapi/scopes` (authenticated) → `[{ "scope", "description", "projectScopes" }]` |
| UI (sample) | `/ui/scopes` — reference page; descriptions also appear as chips/hints on `/ui/tokens/new` |

```bash
curl -u demo:demo http://localhost:8080/api/openapi/scopes
```

```json
[
  { "scope": "payments:read", "description": "Чтение платежей — открывает GET /api/demo/payments", "projectScopes": [] }
]
```

## Documentation

Полная документация — в каталоге [`docs/`](docs/index.md); она же собирается в статический
сайт (MkDocs Material) для публикации на GitHub Pages / GitLab Pages.

| Раздел | Для кого |
|--------|----------|
| [Обзор проекта](docs/index.md) · [Быстрый старт](docs/getting-started.md) · [Обзор функций](docs/features/index.md) · [Конфигурация](docs/configuration.md) | все |
| [Бизнес-аналитику](docs/guides/business-analyst.md) | возможности, правила, сценарии, критерии приёмки |
| [Системному аналитику](docs/guides/system-analyst.md) | модель данных, контракты, потоки, НФТ |
| [Фронтенд-разработчику](docs/guides/frontend.md) | интеграция UI по REST API |
| [Бэкенд-разработчику](docs/guides/backend.md) | встраивание, безопасность, расширения, БД |
| [Web UI (server-side)](docs/guides/web-ui.md) · [Keycloak](docs/guides/keycloak.md) | референсный UI и режим `keycloak` |
| [REST API](docs/api/rest-api.md) · [Java API](docs/api/java-api.md) · [Порты и SPI](docs/api/spi.md) | справочники |
| [Тестирование](docs/operations/testing.md) · [Публикация документации](docs/operations/publishing.md) · [Ограничения и roadmap](docs/operations/roadmap.md) | эксплуатация и развитие |

Ключевые известные ограничения (скоупы не валидируются → возможен `ROLE_ADMIN`;
`tenantId` из запроса; статус `EXPIRED` не выставляется; мёртвые свойства `audit.*`,
`rate-limit.backend`, `token.default-ttl`) собраны в
[«Ограничения и roadmap»](docs/operations/roadmap.md).

```bash
# локальный предпросмотр сайта документации
python3 -m venv .venv-docs && source .venv-docs/bin/activate
pip install -r requirements-docs.txt
mkdocs serve           # http://127.0.0.1:8000
mkdocs build --strict  # сборка в ./site
```

Публикация: GitHub — [`.github/workflows/docs.yml`](.github/workflows/docs.yml)
(Settings → Pages → Source: GitHub Actions); GitLab — job `pages` в
[`.gitlab-ci.yml`](.gitlab-ci.yml) (артефакт `public`). Детали —
[Публикация документации](docs/operations/publishing.md).

## Build & test

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test
```

Coverage baseline: unit tests on hasher, scope resolvers, rate limiter, `DefaultApiTokenService`, security authenticator; Testcontainers for JPA; ApplicationContextRunner for autoconfigure enable/disable.

## Disable starter

```yaml
openapi.tokens.enabled: false
```

No token-related beans are registered.

## Sample app

Module `openapi-tokens-sample` — минимальное Spring Boot приложение: REST API статера **плюс
server-rendered web UI** для пользователя и администратора (Thymeleaf, локальный CSS, без CDN).

```bash
docker compose up -d
./gradlew :openapi-tokens-sample:bootRun
```

Users: `demo`/`demo` (USER), `admin`/`admin` (ADMIN).

### Web UI

Вход: <http://localhost:8080/login> (form login, session, CSRF; REST API продолжает работать
через HTTP Basic / Bearer).

| Раздел | URL | Кто видит | Что умеет |
|--------|-----|-----------|-----------|
| Справочник скоупов | `/ui/scopes` | любой аутентифицированный | скоупы с описанием и правами (данные `GET /api/openapi/scopes`) |
| Кабинет пользователя | `/ui/tokens` | любой аутентифицированный | список своих токенов, счётчики по статусам, поиск и фильтр по статусу |
| Создание токена | `/ui/tokens/new` | любой аутентифицированный | `name`, `description`, `scopes` (чипы и описания из справочника), абсолютный срок, sliding TTL, rate limit, `tenantId` |
| Показ секрета | `/ui/tokens/created` | владелец | raw-токен показывается один раз + кнопка «Скопировать» |
| Карточка токена | `/ui/tokens/{id}` | владелец | метаданные, статистика использования, журнал аудита (50 записей), отзыв |
| Админка | `/admin/tokens` | `ROLE_ADMIN` | все токены всех владельцев, счётчики, фильтры по тексту / владельцу / статусу |
| Карточка в админке | `/admin/tokens/{id}` | `ROLE_ADMIN` | метаданные владельца, статистика, аудит (100 записей), блокировка, разблокировка, принудительный отзыв |

UI ходит в те же бины (`ApiTokenService`, `AuditLogRepository`), что и REST-контроллеры
`openapi-tokens-web`, поэтому квоты, проверки владельца, TTL и аудит работают одинаково.
Полезные страницы: `/` (редирект по роли), `/error` → `error/403`, `error/404`.

```bash
# create token via REST
curl -u demo:demo -H 'Content-Type: application/json' \
  -d '{"name":"ci","scopes":["payments:read"]}' \
  http://localhost:8080/api/openapi/tokens

# call protected demo API
curl -H "Authorization: Bearer atk_…" http://localhost:8080/api/demo/payments
```

Полный пример с Keycloak (OIDC login + JWT на API) — модуль `openapi-tokens-sample-keycloak`,
см. раздел «Sample app with Keycloak» ниже.

Ключевые классы UI: `ru.openapi.tokens.sample.ui.*` в модуле `openapi-tokens-sample-ui`
(`UserTokenViewController`, `AdminTokenViewController`, `ScopeCatalogViewController`,
`TokenUiMapper`, `TokenUiSecurity`), шаблоны — `openapi-tokens-sample-ui/src/main/resources/templates`,
стили — `.../static/css/app.css`. Тесты — `@WebMvcTest` слайсы
(`UserTokenViewControllerTest`, `AdminTokenViewControllerTest`, `ScopeCatalogViewControllerTest`)
и проверка доступов в семпле (`UiAccessControlTest`).

## Sample app with Keycloak

Модуль `openapi-tokens-sample-keycloak`: тот же функционал, что у первого семпла (REST + общий web UI),
но аутентификация — через Keycloak: `openapi.tokens.auth.mode=keycloak`, вход в UI по
authorization code flow, REST API принимает **и** Keycloak access token, **и** API-токен сервиса.

```bash
# Postgres (5433) + Keycloak (8180) с импортом realm "openapi-tokens"
docker compose --profile keycloak up -d
./gradlew :openapi-tokens-sample-keycloak:bootRun
```

- UI: <http://localhost:8081> → редирект на Keycloak, пользователи `demo`/`demo` (роль `USER`) и
  `admin`/`admin` (роли `ADMIN`, `USER`). Владелец токена = claim `sub`, tenant = `tenant_id`.
- Keycloak admin console: <http://localhost:8180> (`admin`/`admin`).
- Realm `openapi-tokens` импортируется из `openapi-tokens-sample-keycloak/docker/keycloak/realm-openapi-tokens.json`:
  роли `USER`/`ADMIN`, пользователи `demo`/`admin`, клиенты `openapi-tokens-ui` (authorization code)
  и `openapi-tokens-api` (direct access grants + client credentials), client scope `tenant`
  (claim `tenant_id`) и `roles-to-token` (реальм-роли в ID token/userinfo).

```bash
# 0. client credentials (service account) — тоже поддержано
curl -s -X POST http://localhost:8180/realms/openapi-tokens/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=openapi-tokens-api -d client_secret=api-secret | jq -r .access_token

# 1. access token Keycloak (direct access grants)
KJWT=$(curl -s -X POST http://localhost:8180/realms/openapi-tokens/protocol/openid-connect/token   -d grant_type=password -d client_id=openapi-tokens-api -d client_secret=api-secret   -d username=demo -d password=demo | jq -r .access_token)

# 2. создать API-токен от имени Keycloak-пользователя (ownerId = sub)
curl -H "Authorization: Bearer $KJWT" -H 'Content-Type: application/json'   -d '{"name":"ci","scopes":["payments:read"]}'   http://localhost:8081/api/openapi/tokens

# 3. вызвать защищённый ресурс API-токеном (scope payments:read)
curl -H "Authorization: Bearer atk_…" http://localhost:8081/api/demo/payments

# 4. кто я: sub / tenant / authorities
curl -H "Authorization: Bearer $KJWT" http://localhost:8081/api/demo/whoami
```

Что нужно сделать приложению в `keycloak`-режиме (готовый пример — `KeycloakSecurityConfig`):

1. **Своя цепочка для `/api/openapi/**`** с `oauth2ResourceServer().jwt()` и тем же именем бина
   `openapiTokensSecurityFilterChain` — дефолтная цепочка стартера умеет только HTTP Basic.
   `ApiTokenAuthenticationFilter` добавляется в неё вручную (ключи `atk_…` работают как раньше).
2. **Маппинг ролей Keycloak в authorities**: `realm_access.roles` → `ROLE_*`
   (`KeycloakJwtAuthenticationConverter` для JWT, `KeycloakGrantedAuthoritiesMapper` для OIDC-логина),
   иначе `/admin/**` и `@PreAuthorize("hasRole('ADMIN')")` не сработают.
3. **Claim `tenant_id` в токене** (realm-export уже содержит client scope `tenant`).
4. Порядок цепочек: `@Order(1)` — `/api/openapi/**`, `@Order(TokenUiSecurity.UI_FILTER_CHAIN_ORDER)` —
   UI, `@Order(LOWEST_PRECEDENCE)` — остальное (`/api/demo/**`).
5. **Bearer token resolver должен пропускать API-токены.** OAuth2 resource server пытается декодировать
   любой `Bearer`-токен как JWT: `atk_…` даст `401 invalid_token` ещё до авторизации. В семпле это
   решено обёрткой `apiTokenAwareBearerTokenResolver()` (ключи `atk_…` уходят в
   `ApiTokenAuthenticationFilter`, JWT — в resource server).
6. **Имя принципала для UI**: у OIDC-логина principal name по умолчанию `sub` (UUID). В семпле
   `keycloakOidcUserService()` пересобирает `DefaultOidcUser` с ключом `preferred_username`, поэтому
   в шапке UI видно имя пользователя, а не UUID.

Токен-специфичные проверки без живого Keycloak: `KeycloakApiSecurityTest` (Keycloak JWT → owner из
`sub`, роли, scope-авторизация) и `KeycloakUiSecurityTest` (редирект на Keycloak, `ROLE_ADMIN` для
админки) — используется `SecurityMockMvcRequestPostProcessors.jwt()`.

Realm-export в репозитории сгенерирован через Keycloak Admin API (`partial-export`): при импорте
JSON Keycloak **не создаёт** встроенные client scope (`basic`, `roles`, `profile`, …), поэтому в файле
они присутствуют явно — иначе в токенах не будет `sub` и `realm_access`.

