# Порты и точки расширения

Каждый значимый компонент библиотеки объявлен как интерфейс (порт) в `openapi-tokens-core`
и реализуется по умолчанию одним из модулей. Авто-конфигурация создаёт все бины
с `@ConditionalOnMissingBean`, поэтому **свой бин с тем же типом полностью заменяет
реализацию по умолчанию** — без форка библиотеки и без исключения авто-конфигурации.

## Список портов

| Порт | Назначение | Реализация по умолчанию | Где объявлен бин |
|---|---|---|---|
| `ApiTokenService` | Жизненный цикл токенов | `TransactionalApiTokenService` → `DefaultApiTokenService` | `OpenApiTokensAutoConfiguration#apiTokenService` |
| `ApiTokenRepository` | Хранение токенов | `JpaApiTokenRepository` | `OpenApiTokensPersistenceJpaConfig` (scan) |
| `AuditLogRepository` | Хранение/агрегация аудита | `JpaAuditLogRepository` | там же |
| `ScopeCatalogRepository` | Описания скоупов | `JpaScopeCatalogRepository` | там же |
| `ScopeMappingRepository` | Маппинги 1:M | `JpaScopeMappingRepository` | там же |
| `TokenHasher` | Хеширование секретов | `AlgorithmTaggedTokenHasher` (тег `{argon2}`/`{bcrypt}`) | `#tokenHasher` |
| `RateLimiter` | Лимиты на токен и пред-аутентификационные лимиты | `Bucket4jRateLimiter` (in-memory) | `#rateLimiter` |
| `ScopeResolver` | Скоупы → authority | `IdentityScopeResolver` / `MappedScopeResolver` (по свойству) | `#scopeResolver` |
| `ScopeMappingSource` | Источник маппингов | `CompositeMappingSource` (конфиг + БД) | `#scopeMappingSource` |
| `ScopeCatalog` | Read-API справочника | `DefaultScopeCatalog` | `#scopeCatalog` |
| `ScopeValidator` | Политика валидации скоупов при создании | `CompositeScopeValidator` (`DenyPattern` + `Catalog` + `CallerAuthority`) или `ScopeValidator.allowAll()` при `mode=off` | `#scopeValidator` |
| `CallerAuthorityResolver` | Authority вызывающего для `strict`-валидации | `SecurityContextCallerAuthorityResolver` | `#callerAuthorityResolver` |
| `TokenOwnerResolver` | Владелец из security context | `InternalTokenOwnerResolver` / `KeycloakTokenOwnerResolver` | `#internalTokenOwnerResolver` / `#keycloakTokenOwnerResolver` |
| `AuditRecorder` | Запись событий | `AsyncAuditRecorder` / `SyncAuditRecorder` / `NoOpAuditRecorder` (по `audit.*`) | `#auditRecorder` |
| `ApiTokenAuthenticator` | Проверка сырого токена | `DefaultApiTokenAuthenticator` | `#apiTokenAuthenticator` |
| `ApiTokenPermissionChecker` | Программные проверки прав | `ApiTokenPermissionEvaluator` | `#apiTokenPermissionChecker` |
| `PermissionEvaluator` | SpEL `hasPermission(...)` | тот же `ApiTokenPermissionEvaluator` | `#apiTokenPermissionEvaluator` |
| `Clock` | Источник времени | `Clock.systemUTC()` | `#openApiTokensClock` |
| `Executor` (`openapiTokensAuditExecutor`) | Пул потоков аудита | `ThreadPoolTaskExecutor` (core 2, max 4) | `#openapiTokensAuditExecutor` |
| `SecurityFilterChain` (`openapiTokensSecurityFilterChain`) | Защита `/api/openapi/**` | цепочка с Basic + фильтром + `ROLE_ADMIN` | `#openapiTokensSecurityFilterChain` |

Контроллеры, `ApiTokenWebMapper` и `ApiTokenExceptionHandler` тоже создаются через
`@ConditionalOnMissingBean` — их можно подменить своими бинами (например, чтобы изменить
контракт или добавить `@PreAuthorize`).

## Как переопределить бин

Достаточно объявить бин нужного типа в конфигурации приложения:

```java
@Configuration
class MyTokenConfig {

    @Bean
    TokenHasher tokenHasher() {
        return new TokenHasher() {
            @Override public String hash(String rawSecret) { … }
            @Override public boolean matches(String rawSecret, String tokenHash) { … }
        };
    }
}
```

Правила:

1. Имя бина может быть любым — важен **тип**.
2. Для бинов, объявленных по имени, имя совпадает с именем метода:
   `openapiTokensSecurityFilterChain`, `openapiTokensAuditExecutor`, `openApiTokensClock`.
   Чтобы заменить такой бин, нужно **то же имя**.
3. Заменяя низкоуровневый компонент, убедитесь, что он совместим с вызывающим кодом
   (например, свой `ApiTokenRepository` должен возвращать доменные `ApiToken` со скоупами).

## Типовые сценарии

=== "Redis / распределённый rate limiter"

    ```java
    @Bean
    RateLimiter rateLimiter(RedisTemplate<String, String> redis) {
        // своя реализация: общий счётчик на все узлы приложения
        return new RedisRateLimiter(redis);
    }
    ```

    Свойство `openapi.tokens.rate-limit.backend` при этом не используется —
    выбор реализации определяется наличием бина.

=== "Отключить аудит (или сделать его синхронным)"

    Проще всего — свойствами:

    ```yaml
    openapi:
      tokens:
        audit:
          enabled: false        # NoOpAuditRecorder
          async: false          # SyncAuditRecorder: запись в потоке запроса
          record-failures: false
    ```

    Свой бин нужен, только если требуется иная логика (например, фильтрация событий):

    ```java
    @Bean
    AuditRecorder auditRecorder(AuditLogRepository repository) {
        return event -> { /* no-op или своя обработка */ };
    }
    ```

    Пользовательский бин имеет приоритет над настройками `audit.*`.

=== "Своя политика валидации скоупов"

    ```java
    @Bean
    ScopeValidator scopeValidator(MyPolicyService policy) {
        return context -> policy.check(
                context.ownerId(),
                context.tenantId(),
                context.requestedScopes(),
                context.callerAuthorities(),
                context.privileged());
    }
    ```

    `ScopeValidationContext` содержит `ownerId`, `tenantId`, `requestedScopes`,
    `callerAuthorities` и `privileged`. Реализация обязана падать закрыто: бросать
    `InvalidScopeException` (`UNKNOWN_SCOPE`/`DENIED_SCOPE`/`NOT_GRANTABLE`), а не молча
    расширять права. Свой бин полностью заменяет сборку по `scopes.validation.mode`.

=== "Authority вызывающего не из SecurityContext"

    ```java
    @Bean
    CallerAuthorityResolver callerAuthorityResolver(TenantContext tenantContext) {
        return new CallerAuthorityResolver() {
            @Override public Set<String> currentAuthorities() { return tenantContext.authorities(); }
            @Override public boolean isPrivileged() { return tenantContext.isAdmin(); }
        };
    }
    ```

    По умолчанию authority берутся из текущего Spring Security context
    (`SecurityContextCallerAuthorityResolver`), а «привилегированность» определяется по
    `scopes.validation.privileged-authorities`.

=== "Хранение не в PostgreSQL"

    ```java
    @Bean
    ApiTokenRepository apiTokenRepository(MongoTemplate mongo) {
        return new MongoApiTokenRepository(mongo);   // реализует порт
    }

    @Bean
    AuditLogRepository auditLogRepository(MongoTemplate mongo) {
        return new MongoAuditLogRepository(mongo);
    }
    ```

    Обязательных методов в `ApiTokenRepository` семь: `save`, `findById`, `findByPrefix`,
    `findByOwnerId`, `findAll`, `countByOwnerId`, `deleteById`. Остальные — `findPage`,
    `count`, `countUsableByOwnerId`, `lockOwnerQuota`, `touchLastUsed` — имеют реализации по
    умолчанию (см. [ниже](#изменения-в-портах-после-усиления-безопасности)); `AuditLogRepository`
    состоит из 3 методов.
    Если JPA не нужна, исключите `OpenApiTokensPersistenceAutoConfiguration` (см. ниже)
    или просто перекройте все четыре порта хранения своими бинами.

=== "Свой владелец (не из SecurityContext)"

    ```java
    @Bean
    TokenOwnerResolver tokenOwnerResolver(TenantContext tenantContext) {
        return () -> new TokenOwner(
                tenantContext.currentUserId(),
                tenantContext.currentTenantId()
        );
    }
    ```

    Режим `openapi.tokens.auth.mode` определяет только реализацию по умолчанию — ваш бин
    выигрывает в любом случае.

=== "Своя логика прав"

    ```java
    @Bean
    ScopeResolver scopeResolver(MyPermissionService permissions) {
        return (tokenScopes, tenantId) -> permissions.expand(tokenScopes, tenantId);
    }
    ```

=== "Справочник из внешнего сервиса"

    ```java
    @Bean
    ScopeCatalog scopeCatalog(ExternalRegistry registry) {
        return () -> registry.describeScopes().stream()
                .map(dto -> new ScopeInfo(dto.name(), dto.description(), dto.authorities()))
                .toList();
    }
    ```

=== "Свой HTTP-контракт"

    ```java
    @Bean
    UserApiTokenController userApiTokenController(ApiTokenService service,
                                                ApiTokenWebMapper mapper,
                                                AuditLogRepository audit) {
        return new UserApiTokenController(service, mapper, audit) {
            // либо собственный контроллер с нужными @PreAuthorize/DTO
        };
    }
    ```

    Более чистый путь — исключить автоконфигурацию статера и написать свои контроллеры
    поверх `ApiTokenService`.

## Как исключить авто-конфигурацию

=== "Отключить всё"

    ```yaml
    openapi.tokens.enabled: false
    ```

    Не создаётся ни один бин статера (включая `SecurityFilterChain` и фильтр).

=== "Исключить часть авто-конфигураций"

    ```java
    @SpringBootApplication(exclude = {
            OpenApiTokensPersistenceAutoConfiguration.class
    })
    class Application { }
    ```

    Имя класса удобно задать строкой, чтобы не тянуть зависимость на модуль конфигурации:

    ```yaml
    spring:
      autoconfigure:
        exclude: ru.openapi.tokens.autoconfigure.OpenApiTokensPersistenceAutoConfiguration
    ```

## Условия активации бинов

Понимание условий помогает предсказуемо переопределять бины:

| Бин | Условия |
|---|---|
| Все бины `OpenApiTokensAutoConfiguration` | `openapi.tokens.enabled=true` (или не задано) |
| Все бины `OpenApiTokensPersistenceAutoConfiguration` | `enabled=true` + `EntityManager` на classpath + бин `DataSource` |
| `internalTokenOwnerResolver` | дополнительно `openapi.tokens.auth.mode=internal` (или не задано) |
| `keycloakTokenOwnerResolver` | дополнительно `auth.mode=keycloak` + наличие `JwtAuthenticationToken` |
| `scopeResolver` | Возвращает `MappedScopeResolver`, если `scopes.mode=mapped`, иначе `IdentityScopeResolver` (значение — enum, опечатка = ошибка старта) |
| `scopeValidator` | `ScopeValidator.allowAll()` при `scopes.validation.mode=off`; иначе композиция `DenyPatternScopeValidator` + (для `catalog`/`strict`) `CatalogScopeValidator` + (для `strict`) `CallerAuthorityScopeValidator` |
| `callerAuthorityResolver` | `@ConditionalOnMissingBean`; по умолчанию `SecurityContextCallerAuthorityResolver` с `scopes.validation.privileged-authorities` |
| `auditRecorder` | `NoOpAuditRecorder` при `audit.enabled=false`, `SyncAuditRecorder` при `audit.async=false`, иначе `AsyncAuditRecorder` |
| `apiTokenPermissionEvaluator` / `apiTokenPermissionChecker` | `@ConditionalOnMissingBean`; один и тот же экземпляр доступен как `PermissionEvaluator` и `ApiTokenPermissionChecker` |
| `openapiTokensSecurityFilterChain` | `@ConditionalOnBean(HttpSecurity.class)` — то есть при наличии Spring Security MVC; отключается своим бином с этим именем |
| `apiTokenAuthenticationFilter` | `@ConditionalOnMissingBean` |
| `openapiTokensAuditExecutor` | `@ConditionalOnMissingBean(name = "openapiTokensAuditExecutor")` |

!!! note "`@ConditionalOnBean(HttpSecurity.class)`"
    Если в приложении нет бина `HttpSecurity` (например, чистая реактивная конфигурация
    или отсутствие Spring Security), готовая цепочка не создаётся — контроллеры при этом
    работают, но защищать их придётся самостоятельно.

## Изменения в портах после усиления безопасности

Новые и изменённые методы портов. Все они, кроме явно помеченных, имеют реализации по
умолчанию, поэтому существующие пользовательские адаптеры продолжают компилироваться.

| Порт / метод | Назначение |
|---|---|
| `ScopeValidator#validate(ScopeValidationContext)` | Новый порт: политика валидации скоупов при создании токена. `ScopeValidationContext` — `ownerId`, `tenantId`, `requestedScopes`, `callerAuthorities`, `privileged` |
| `CallerAuthorityResolver#currentAuthorities()` / `isPrivileged()` | Новый порт: откуда берутся authority вызывающего для `strict`. Реализация по умолчанию — `SecurityContextCallerAuthorityResolver` |
| `ApiTokenRepository#findPage(offset, limit)` | Батчевая страница токенов для админского списка (default-реализация нарезает `findAll()`) |
| `ApiTokenRepository#count()` | Общее число токенов (для заголовка `X-Total-Count`) |
| `ApiTokenRepository#countUsableByOwnerId(ownerId)` | Квота: только `ACTIVE` + `BLOCKED`; отозванные/просроченные слот не занимают |
| `ApiTokenRepository#lockOwnerQuota(ownerId)` | Сериализация создания токенов на владельца. В JPA-адаптере — `pg_advisory_xact_lock(hashtext(ownerId))` (PostgreSQL), снимается с концом транзакции |
| `ApiTokenRepository#touchLastUsed(id, lastUsedAt)` | Точечный `UPDATE ... WHERE status = 'ACTIVE'` вместо read-modify-write: конкурентный отзыв не перетирается |
| `ScopeMappingSource#projectScopesFor(Set<String>, tenantId)` | Батчевый резолв маппингов одним запросом; default-реализация вызывает поштучный вариант |
| `RateLimiter#tryAcquire(String key, policy)` | Лимит по произвольному ключу (пред-аутентификационный `auth-fail:<prefix>`) |
| `RateLimiter#isExhausted(String key, policy)` | Нерасходующая проверка бюджета перед вычислением хеша (по умолчанию `false` — лимит просто не отсекает запрос заранее) |
| `RateLimiter#reset(String key)` | Сброс состояния ведра для ваших сценариев (библиотека его не вызывает); по умолчанию — no-op |
| `ApiTokenService#listAll(offset, limit)` / `countAll()` | Постраничный админский список и общее число токенов |
| `TokenHasherFactory.createTagged(algorithm)` | Алгоритм-тегированный хешер: `{argon2}`/`{bcrypt}` в начале хеша; старые хеши без тега распознаются по нативному префиксу. Сам порт `TokenHasher` (`hash`/`matches`) не изменился |
| `ApiTokenPermissionEvaluator` | Регистрируется и как бин `PermissionEvaluator`, и как `ApiTokenPermissionChecker`. `hasPermission(authentication, …)` использует **переданную** аутентификацию — выражение можно применять в `@PreAuthorize("hasPermission(...)")` при включённом method security |

!!! note "Обратная совместимость адаптеров"
    `lockOwnerQuota` по умолчанию — no-op, `touchLastUsed` — legacy read-modify-write,
    `findPage`/`count` — над `findAll()`, `countUsableByOwnerId` — над `countByOwnerId`.
    Если вы писали свой `ApiTokenRepository` для не-PostgreSQL хранилища, квота и пагинация
    будут работать, но без транзакционной сериализации и точечного UPDATE — переопределите
    методы, если важны конкурентные сценарии.

## Чего расширить нельзя

| Ограничение | Комментарий |
|---|---|
| Формат токена (`atk_<prefix>_<secret>`, схема `atk`, длина секрета 32 байта) | Зашит в `RawToken`; изменить можно только своей реализацией `ApiTokenAuthenticator` и своим форматом |
| Regex-валидация сырого токена | Константа `RawToken.TOKEN_PATTERN` |
| Порядок проверок в аутентификаторе | Реализация `DefaultApiTokenAuthenticator`; замена — свой `ApiTokenAuthenticator` |
| Поля REST-DTO | Нужен свой контроллер/маппер |
| Миграции Flyway | Файлы в jar библиотеки; правятся только своей миграцией поверх или исключением JPA-модуля |
| Схема таблиц (имена/типы) | Порты можно заменить, схему — только своими миграциями |

## Связанные разделы

- [Java API](java-api.md) — что доступно из кода.
- [Конфигурация](../configuration.md) — свойства и их фактическое влияние.
- [Бэкенд-разработчику](../guides/backend.md) — практические рецепты интеграции.
- [Обзор архитектуры](../architecture/index.md) — слои и зависимости.
