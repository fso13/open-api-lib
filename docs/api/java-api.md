# Java API

Библиотека доступна не только по HTTP: те же операции можно вызывать напрямую из кода
продукта. Это удобно, когда токены нужно выпускать программно (например, при создании
интеграции в админке продукта) или когда UI рендерится на сервере.

Все бины создаются авто-конфигурацией; инъекция — обычная (конструкторная).

## `ApiTokenService` — жизненный цикл токенов

```java
public interface ApiTokenService {

    CreatedApiToken create(CreateTokenCommand command);
    ApiToken get(UUID tokenId);
    List<ApiToken> listOwn();
    ApiToken revoke(RevokeTokenCommand command);
    ApiToken block(BlockTokenCommand command);
    TokenUsageStats usageStats(UUID tokenId);

    // административные операции (без проверки владельца)
    List<ApiToken> listAll();
    List<ApiToken> listAll(int offset, int limit);   // постранично, новые первыми
    long countAll();                                 // общее число токенов
    ApiToken getAny(UUID tokenId);
    ApiToken forceRevoke(RevokeTokenCommand command);
    ApiToken forceBlock(BlockTokenCommand command);

    // служебное: продление скользящего TTL после успешной аутентификации
    ApiToken touchLastUsed(UUID tokenId);
}
```

| Метод | Проверка владельца | Транзакция | Исключения |
|---|---|---|---|
| `create` | владелец = текущий security context | `@Transactional` | `InvalidScopeException`, `TenantNotAllowedException`, `QuotaExceededException`, `EmptyScopesException` |
| `get` | да (чужой → `ApiTokenNotFoundException`) | read-only | `ApiTokenNotFoundException` |
| `listOwn` | фильтр по владельцу | read-only | — |
| `revoke` | да | `@Transactional` | `ApiTokenNotFoundException`, `InvalidTokenStateException` |
| `block` | да | `@Transactional` | `ApiTokenNotFoundException`, `InvalidTokenStateException` |
| `usageStats` | да | read-only | `ApiTokenNotFoundException` |
| `listAll()` / `listAll(offset, limit)` | **нет** | read-only | — |
| `countAll()` | **нет** | read-only | — |
| `getAny` | **нет** | read-only | `ApiTokenNotFoundException` |
| `forceRevoke` / `forceBlock` | **нет** | `@Transactional` | `ApiTokenNotFoundException`, `InvalidTokenStateException` |
| `touchLastUsed` | **нет** | `@Transactional` | `ApiTokenNotFoundException`, `InvalidTokenStateException` |

Порядок проверок в `create`: разрешение `tenantId` (чужой тенант → `TenantNotAllowedException`),
затем **валидация скоупов** (`InvalidScopeException`), и только потом квота и генерация секрета —
при отказе валидации в БД ничего не записывается. Квота считается по «рабочим» токенам
(`ACTIVE` + `BLOCKED`), поэтому отзыв освобождает слот. Если в команде не заданы ни `expiresAt`,
ни `slidingTtl`, подставляется `openapi.tokens.token.default-ttl`.

!!! warning "Административные методы не проверяют права"
    `listAll`, `getAny`, `forceRevoke`, `forceBlock` не выполняют **никаких** проверок
    безопасности: они предполагают, что вызывающий код уже убедился в правах администратора.
    Если вызывать их из своего эндпоинта, защиту нужно обеспечить самому
    (`@PreAuthorize("hasRole('ADMIN')")` и т. п.).

### Пример: выпуск токена из кода

```java
@Service
class IntegrationService {

    private final ApiTokenService apiTokenService;

    IntegrationService(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    String issueTokenForCurrentUser(String name) {
        CreateTokenCommand command = new CreateTokenCommand(
                name,                                   // имя (обязательно)
                "Выпущен из админки интеграций",        // описание
                Set.of("payments:read"),                // скоупы (непустые!)
                Instant.now().plus(365, ChronoUnit.DAYS), // абсолютный TTL
                Duration.ofHours(1),                    // скользящий TTL
                new RateLimitPolicy(100, 60),           // 100 запросов в минуту
                null                                    // tenantId: null → из владельца
        );

        CreatedApiToken created = apiTokenService.create(command);
        String rawToken = created.rawToken().value();   // показать один раз!
        UUID id = created.token().id();
        return rawToken;
    }
}
```

Сокращённый конструктор команды:

```java
CreateTokenCommand command = CreateTokenCommand.of("ci-bot", Set.of("payments:read"));
```

### Пример: чтение и управление

```java
List<ApiToken> myTokens = apiTokenService.listOwn();

ApiToken token = apiTokenService.get(tokenId);
if (token.status() == TokenStatus.ACTIVE) {
    apiTokenService.block(BlockTokenCommand.block(tokenId));
}

TokenUsageStats stats = apiTokenService.usageStats(tokenId);
log.info("token {} used {} times, last at {}", tokenId, stats.totalRequests(), stats.lastUsedAt());
```

## Доменная модель

### `ApiToken` — агрегат токена

Иммутабельный объект; переходы возвращают новый экземпляр и проверяют инварианты.

```java
token.id();                 // UUID
token.name();
token.description();
token.credentials();        // TokenCredentials(prefix, tokenHash)
token.ownerId();
token.tenantId();
token.status();             // TokenStatus, как хранится в БД
token.effectiveStatus(Instant.now());  // ACTIVE → EXPIRED, если expiresAt уже прошёл
token.scopes();             // Set<String> (unmodifiable)
token.expiry();             // TokenExpiry
token.rateLimit();          // RateLimitPolicy
token.createdAt();
token.revokedAt();
token.createdBy();

token.isUsable();           // status == ACTIVE
token.revoke(Instant.now());
token.block();
token.unblock();
token.markExpired();
token.touchLastUsed(Instant.now());
ApiToken copy = token.toBuilder().description("новое описание").build();
```

!!! danger "Секрета в агрегате нет"
    `ApiToken` хранит только префикс и хеш (`TokenCredentials`). Сырой секрет доступен
    **исключительно** в `CreatedApiToken#rawToken()` сразу после создания.

### `RawToken` — сырой токен

```java
RawToken generated = RawToken.generate(8);   // prefixLength ∈ [4, 16]
generated.value();                            // "atk_<prefix>_<secret>"
generated.prefix();
generated.secret();

RawToken parsed = RawToken.parse("atk_9f3c1a7b_5d8e…");   // InvalidTokenFormatException при ошибке
RawToken.SCHEME;                              // "atk"
```

### `TokenExpiry` — TTL

```java
TokenExpiry.none();
TokenExpiry.ofAbsolute(Instant.parse("2027-01-01T00:00:00Z"));
TokenExpiry.ofSliding(Duration.ofHours(1), lastUsedAt);
TokenExpiry.of(expiresAt, slidingTtl, lastUsedAt);

expiry.isExpired(Clock.systemUTC());
expiry.expiresAt();
expiry.slidingTtl();
expiry.slidingTtlSeconds();   // Integer, null если скользящего нет
expiry.lastUsedAt();
```

### `RateLimitPolicy`

```java
RateLimitPolicy.unlimited();          // (null, null) → лимит не применяется
new RateLimitPolicy(100, 60);         // 100 запросов / 60 секунд
policy.isConfigured();                // true, если заданы оба поля
```

## `ScopeCatalog` — справочник скоупов

```java
@Autowired ScopeCatalog scopeCatalog;

List<ScopeInfo> scopes = scopeCatalog.listScopes();
for (ScopeInfo scope : scopes) {
    scope.scope();            // "payment:read"
    scope.description();      // может быть null
    scope.projectScopes();    // Set<String>, пусто в identity-режиме
    scope.hasDescription();
}
```

Список отсортирован по имени и содержит объединение описанных скоупов и скоупов из маппингов.

## `ApiTokenPermissionChecker` — проверки прав в коде

```java
@Autowired ApiTokenPermissionChecker checker;

checker.hasAuthority("readPayment");
checker.hasAnyAuthority("createPayment", "refundPayment");
```

Реализация по умолчанию (`ApiTokenPermissionEvaluator`) читает authority из текущего
`SecurityContext`. Тот же экземпляр зарегистрирован и как бин `PermissionEvaluator`, поэтому
при включённом method security работает и в SpEL:

```java
@PreAuthorize("hasPermission(#tokenId, 'ApiToken', 'tokens:revoke')")
void revokeToken(UUID tokenId) { … }
```

`hasPermission(...)` использует переданную аутентификацию (а если она `null` — текущий
`SecurityContext`), поэтому проверка не «прилипает» к чужому принципалу. Сам evaluator
сравнивает `permission` с authority вызывающего — проверки владения токеном в него не входят.

## `ScopeValidator` и `CallerAuthorityResolver`

Политика валидации скоупов — отдельный порт, который вызывается сервисом до проверки квоты:

```java
public interface ScopeValidator {
    void validate(ScopeValidationContext context);   // бросить InvalidScopeException при отказе
    static ScopeValidator allowAll() { … }
}
```

`ScopeValidationContext(ownerId, tenantId, requestedScopes, callerAuthorities, privileged)` —
всё, что нужно для решения. Свой бин `ScopeValidator` полностью заменяет сборку по
`scopes.validation.mode` (см. [Порты и SPI](spi.md)).

Authority вызывающего для режима `strict` отдаёт `CallerAuthorityResolver`:

```java
@Autowired CallerAuthorityResolver resolver;

resolver.currentAuthorities();   // Set<String>, пусто без аутентификации
resolver.isPrivileged();         // true, если вызывающий в privileged-authorities
```

## `TokenHasher` и `TokenHasherFactory`

```java
TokenHasher hasher = TokenHasherFactory.create(HashingAlgorithm.BCRYPT);   // без тега

// алгоритм-тегированный вариант (использует авто-конфигурация):
TokenHasher tagged = TokenHasherFactory.createTagged(HashingAlgorithm.ARGON2);
tagged.hash("secret");        // "{argon2}$argon2id$…"
tagged.matches("secret", "{bcrypt}$2a$…");   // true — алгоритм берётся из тега
```

Смена `openapi.tokens.hashing.algorithm` не инвалидирует уже выданные токены: старые хеши
проверяются алгоритмом из тега, а хеши без тега — по нативному префиксу (`$argon2…`, `$2a$…`).

## Команды и результаты

| Тип | Назначение |
|---|---|
| `CreateTokenCommand(name, description, scopes, expiresAt, slidingTtl, rateLimit, tenantId)` | Создание токена; `of(name, scopes)` — сокращённый конструктор |
| `RevokeTokenCommand(UUID tokenId)` | Отзыв |
| `BlockTokenCommand(UUID tokenId, boolean block)` | Блокировка/разблокировка; фабрики `block(id)` / `unblock(id)` |
| `CreatedApiToken(ApiToken token, RawToken rawToken)` | Результат создания: агрегат + одноразовый секрет |
| `TokenUsageStats(tokenId, totalRequests, successfulRequests, failedRequests, lastUsedAt)` | Статистика |
| `ScopeInfo(scope, description, projectScopes)` | Элемент справочника |
| `ScopeValidationContext(ownerId, tenantId, requestedScopes, callerAuthorities, privileged)` | Контекст валидации скоупов |
| `TokenOwner(ownerId, tenantId)` | Владелец из security context |
| `AuditEvent(...)` | Событие аудита |

## Исключения

| Исключение | Когда возникает | Сообщение |
|---|---|---|
| `ApiTokenNotFoundException` | Токен не найден **или** принадлежит другому владельцу | `API token not found: {id}` |
| `QuotaExceededException` | Достигнут лимит токенов владельца | `Token quota exceeded for owner '{owner}': max {N}` |
| `EmptyScopesException` | Пустой набор скоупов | `Token must have at least one scope` |
| `InvalidScopeException` | Скоупы не прошли валидацию (`UNKNOWN_SCOPE` / `DENIED_SCOPE` / `NOT_GRANTABLE`); несёт `getReason()` и `getInvalidScopes()` | `Invalid scopes (UNKNOWN_SCOPE): [scope]` |
| `TenantNotAllowedException` | Запрошен чужой `tenantId` при `allow-tenant-override=false` | `Tenant override is not allowed: requested '…', owner tenant '…'` |
| `InvalidTokenStateException` | Недопустимый переход статуса | например `Only ACTIVE tokens can be blocked` |
| `ConcurrentTokenModificationException` | Конкурентное изменение токена (optimistic locking) | — |
| `InvalidTokenFormatException` | Строка не соответствует `atk_<prefix>_<secret>` | `expected format atk_<prefix>_<secret>` |

Все они — `RuntimeException`; в REST-слое их переводит в `ProblemDetail`
`ApiTokenExceptionHandler` (см. [REST API](rest-api.md#ошибки)).

## Репозитории (для собственных запросов)

```java
@Autowired ApiTokenRepository tokenRepository;
//   save, findById, findByPrefix, findByOwnerId, findAll, countByOwnerId, deleteById
//   + default: findPage(offset, limit), count(), countUsableByOwnerId(ownerId),
//     lockOwnerQuota(ownerId), touchLastUsed(id, lastUsedAt)

@Autowired AuditLogRepository auditRepository;   // append/findByTokenId(tokenId, offset, limit)/usageStats
@Autowired ScopeCatalogRepository scopeCatalogRepository;      // findAll/save/deleteByScope
@Autowired ScopeMappingRepository scopeMappingRepository;      // findByTokenScopes/findAll/save/deleteById
```

У `ScopeMappingSource` есть батчевый `projectScopesFor(Set<String>, tenantId)`, у `RateLimiter` —
`tryAcquire(String key, policy)` и `reset(String key)`. Подробнее о контрактах —
[Порты и SPI](spi.md#изменения-в-портах-после-усиления-безопасности).

Это порты ядра; в JPA-профиле за ними стоят адаптеры модуля `openapi-tokens-persistence-jpa`.

## Прямая работа с JPA-сущностями

Сущности (`ApiTokenEntity`, `AuditLogEntity`, `ScopeMappingEntity`, `ScopeCatalogEntity`)
объявлены как `package-private` в `ru.openapi.tokens.persistence.internal`, их геттеры/сеттеры
тоже package-private. **Продукт не должен работать с ними напрямую** — используйте порты
и доменные типы. Чтобы заменить хранение, реализуйте порт (см. [Порты и SPI](spi.md)).

## Связанные разделы

- [Порты и SPI](spi.md) — замена реализаций по умолчанию.
- [REST API](rest-api.md) — HTTP-аналог этих операций.
- [Бэкенд-разработчику](../guides/backend.md) — практические примеры интеграции.
