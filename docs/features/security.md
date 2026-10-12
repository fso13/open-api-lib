# Аутентификация и авторизация

Раздел описывает полный путь токена: формат, хеширование, фильтр Spring Security, режимы
определения владельца, проверку прав и известные риски.

## Формат токена

```
atk_<prefix>_<secret>
│   │        └── 64 hex-символа (32 случайных байта, SecureRandom)
│   └── публичный префикс, по умолчанию 8 hex-символов (4…16)
└── схема ("atk"), константа RawToken.SCHEME
```

Пример: `atk_9f3c1a7b_5d8e…c1` (полная длина секрета — 64 символа).

| Свойство | Значение | Где настроить |
|---|---|---|
| Схема | `atk` (зашита в код) | — |
| Длина префикса | 8, допустимо 4…16 | `openapi.tokens.token.prefix-length` |
| Длина секрета | 32 байта (64 hex) | зашита в `RawToken.DEFAULT_SECRET_BYTES` |
| Регулярное выражение | `^atk_([A-Za-z0-9]{4,16})_([A-Za-z0-9_-]+)$` | зашито в `RawToken.TOKEN_PATTERN` |
| Заголовок | `Authorization` | `openapi.tokens.token.header` |
| Префикс схемы | `Bearer ` (с пробелом) | `openapi.tokens.token.bearer-prefix` |

**Что хранится в БД:**

| Данные | Хранение |
|---|---|
| `prefix` | открытым текстом (`VARCHAR(16)`, уникальный индекс) |
| секрет | только хеш (`token_hash`, Argon2id по умолчанию) |
| сырой токен | **не хранится нигде** |

!!! info "Почему поиск идёт по префиксу"
    Хеш необратим, поэтому найти токен по секрету нельзя. Сначала по префиксу находится
    кандидат (уникальный индекс `uq_api_token_prefix` — быстрый поиск), затем проверяется
    хеш секрета. Префикс — публичная часть, он безопасен для логов и для отображения в UI
    (`prefix` возвращается в API).

## Хеширование секрета

| Алгоритм | Класс | Параметры | Когда выбирать |
|---|---|---|---|
| **Argon2id** (по умолчанию) | `Argon2TokenHasher` | iterations = 3, memory = 64 МиБ, parallelism = 1 | Рекомендуется: устойчив к GPU/ASIC-перебору |
| BCrypt | `BcryptTokenHasher` | cost = 12 | Если Argon2 недоступен в окружении (нативный код, ограничения JVM) |

```yaml
openapi:
  tokens:
    hashing:
      algorithm: argon2   # argon2 | bcrypt
```

!!! warning "Стоимость Argon2 в горячем пути"
    Параметры (3 итерации, 64 МиБ памяти) подобраны для **редких** операций. Хеш проверяется
    при каждом запросе с Bearer-токеном, поэтому под высокой нагрузкой (тысячи RPS)
    CPU и память станут узким местом. Варианты: кешировать результат аутентификации,
    снизить параметры своим бином `TokenHasher` или перейти на BCrypt. От перебора
    «наугад» защищает пре-аутентификационное троттлинг-ограничение
    (`openapi.tokens.auth.failed-attempt-limit`) — оно срабатывает **до** вычисления
    Argon2, см. [Rate limiting](rate-limiting.md#пре-аутентификационное-ограничение).

Хеши хранятся **с тегом алгоритма**:

| Формат | Когда появляется | Как проверяется |
|---|---|---|
| `{argon2}$argon2id$v=19$…` | новые хеши при `hashing.algorithm: argon2` | по тегу, хешером Argon2 |
| `{bcrypt}$2a$12$…` | новые хеши при `hashing.algorithm: bcrypt` | по тегу, хешером BCrypt |
| `$argon2…` / `$2a$…` (без тега) | хеши, созданные до харденинга | алгоритм определяется по «родному» префиксу |

Тег добавляет `TokenHasherFactory.createTagged(algorithm)` (бин `TokenHasher` в
автоконфигурации). Благодаря тегу **смена `openapi.tokens.hashing.algorithm` больше не
инвалидирует уже выпущенные токены**: новый алгоритм применяется только к новым хешам,
а старые продолжают проверяться тем алгоритмом, которым были созданы. Колонка
`token_hash` — `VARCHAR(255)`, тег добавляет к строке несколько символов.

!!! note "Алгоритм из конфигурации — «по умолчанию», а не «только он»"
    `hashing.algorithm` определяет алгоритм **новых** хешей. Замена бина `TokenHasher`
    на «сырой» (например, `Argon2TokenHasher`) убирает тег: новые хеши будут без тега,
    а уже записанные тегированные (`{argon2}…`) перестанут проверяться. Меняйте алгоритм
    только через свойство, не подменяя тегированный хешер.

---

## Путь запроса через Spring Security

```mermaid
sequenceDiagram
    autonumber
    participant C as Клиент с Bearer-токеном
    participant F as ApiTokenAuthenticationFilter
    participant AM as ProviderManager
    participant P as ApiTokenAuthenticationProvider
    participant A as DefaultApiTokenAuthenticator
    participant RL as RateLimiter
    participant DB as ApiTokenRepository
    participant H as TokenHasher
    participant SR as ScopeResolver
    participant AR as AuditRecorder

    C->>F: Authorization: Bearer atk_…
    F->>F: заголовок начинается с "Bearer " и "atk_"?
    F->>AM: authenticate(ApiTokenAuthentication.unauthenticated(raw))
    AM->>P: authenticate(...)
    P->>A: authenticate(RawToken.parse(raw))
    A->>RL: tryAcquire("auth-fail:<prefix>", failedAttemptPolicy)
    Note over A,RL: троттлинг ДО Argon2; 0 = выключен
    A->>DB: findByPrefix(prefix)
    A->>A: status ACTIVE? TTL не истёк?
    A->>H: matches(secret, tokenHash)
    A->>RL: tryAcquire(tokenId, policy)
    A->>SR: resolve(scopes, tenantId)
    A->>A: authorities пусты? → 401 (reject-empty-authorities)
    A->>DB: targeted UPDATE last_used_at WHERE status='ACTIVE'
    A-->>P: AuthenticatedToken(token, authorities)
    P-->>F: ApiTokenAuthentication(authenticated)
    F->>F: SecurityContextHolder.setAuthentication(...)
    F->>C: filterChain.doFilter — запрос уходит дальше
    F->>AR: record(AUTHENTICATE, success, response.getStatus())
    Note over F,AR: событие пишется ПОСЛЕ обработки, поэтому статус настоящий
```

Особенности реализации, которые важно знать:

- **Фильтр не является точкой входа (`AuthenticationEntryPoint`)** — он только пытается
  аутентифицировать запрос и, если Bearer-токена нет или он не в формате `atk_`, просто
  пропускает запрос дальше, давая шанс другим механизмам (Basic, сессия, Keycloak).
- **Фильтр не регистрируется глобальным сервлет-фильтром.** Автоконфигурация создаёт
  `FilterRegistrationBean` с `enabled=false`, поэтому `ApiTokenAuthenticationFilter`
  работает только там, куда его явно добавили цепочки безопасности (цепочка статера или
  ваша). Раньше Spring Boot регистрировал его ещё и на `/*`.
- Ошибки фильтр рендерит сам, в формате `application/problem+json`, а не через
  `response.sendError`:
  - **401** + `WWW-Authenticate: Bearer`, тело `{"status":401,"title":"Unauthorized","detail":"Unauthorized"}`;
  - **429** + `Retry-After: <секунды>`, тело `{"status":429,"title":"Too Many Requests","detail":"Rate limit exceeded"}`.
- **Локальный `AuthenticationManager`**: комментарий в автоконфигурации поясняет, что
  `ApiTokenAuthenticationProvider` не регистрируется как бин специально, чтобы не подменить
  глобальный `DaoAuthenticationProvider` и не сломать form-login/httpBasic. Провайдер живёт
  внутри `ProviderManager`, созданного только для фильтра.
- Аутентификация проходит **только** если заголовок начинается с `Bearer ` (значение
  `bearer-prefix`) и сырое значение начинается с `atk_`.
- **Токен без authority не аутентифицируется.** Если `ScopeResolver` вернул пустой набор
  (например, в `mapped`-режиме нет ни одного маппинга), при
  `openapi.tokens.auth.reject-empty-authorities=true` (по умолчанию) аутентификация
  отклоняется с 401. `false` возвращает легаси-поведение: токен аутентифицируется и просто
  не имеет прав.

### Что происходит с `Authentication`

`ApiTokenAuthentication` — наследник `AbstractAuthenticationToken`:

| Метод | Значение |
|---|---|
| `getPrincipal()` | доменный объект `ApiToken` |
| `getCredentials()` | сырой токен (только до аутентификации) |
| `getAuthorities()` | authority, полученные из скоупов через `ScopeResolver` |
| `getApiToken()` | тот же `ApiToken` — удобно для доступа в контроллерах |

---

## Режимы определения владельца

Владелец нужен, чтобы отделять токены разных пользователей и считать квоты.

```yaml
openapi:
  tokens:
    auth:
      mode: internal   # internal | keycloak
```

### `internal` (по умолчанию)

`InternalTokenOwnerResolver` — по порядку:

1. если аутентификация — `ApiTokenAuthentication` и в ней есть доменный токен, владелец
   берётся из самого токена (`ownerId`, `tenantId`) — это позволяет управлять токенами,
   используя другой токен;
2. `UserDetails#getUsername()` в качестве `ownerId`;
3. `String`-principal (непустой);
4. `Authentication#getName()`.

Анонимная аутентификация (`AnonymousAuthenticationToken`) явно **отвергается**
(`IllegalStateException`), как и отсутствие/неаутентифицированный `Authentication`.
Раньше анонимный запрос мог «превратиться» во владельца `anonymousUser` и выпустить
токен от его имени.

`tenantId` в этом режиме всегда `null` (кроме случая с `ApiTokenAuthentication`).

### `keycloak`

`KeycloakTokenOwnerResolver` поддерживает три контекста:

| Контекст | Источник `ownerId` | Источник `tenantId` |
|---|---|---|
| `JwtAuthenticationToken` (resource server, access token) | claim `sub` | claim `tenant_id` (настраивается) |
| `OidcUser` (oauth2Login, authorization code flow) | `getSubject()` | claim `tenant_id` |
| `ApiTokenAuthentication` | `token.ownerId()` | `token.tenantId()` |

```yaml
openapi:
  tokens:
    auth:
      mode: keycloak
      keycloak:
        tenant-claim: tenant_id   # имя claim с идентификатором тенанта
```

Отсутствие `sub` → `IllegalStateException("JWT subject (sub) is missing")`.
Неподдерживаемый тип аутентификации → `IllegalStateException` с описанием фактического
principal. Режим `keycloak` требует наличия `spring-security-oauth2-resource-server`
на classpath (в автоконфигурации стоит `@ConditionalOnClass`): иначе бин владельца не создастся
и приложение не поднимется с понятной ошибкой отсутствия `TokenOwnerResolver`.

!!! danger "В режиме `keycloak` готовую цепочку статера нужно заменить"
    Она умеет только HTTP Basic и не принимает JWT. Объявите свой бин
    `openapiTokensSecurityFilterChain` с `oauth2ResourceServer().jwt()` и добавьте в него
    `ApiTokenAuthenticationFilter`, иначе `atk_…` перестанут работать. Также нужен маппинг
    ролей Keycloak в `ROLE_*` — иначе не сработают `hasRole("ADMIN")` и админский UI.

    Готовый разбор с кодом, realm-конфигурацией и тремя цепочками безопасности —
    [Keycloak: интеграция](../guides/keycloak.md).

!!! note "Тенант из запроса проверяется (fail-closed)"
    При создании токена `tenantId` из запроса сверяется с тенантом владельца. По умолчанию
    (`openapi.tokens.scopes.allow-tenant-override=false`) «чужой» тенант даёт **403**
    (`TenantNotAllowedException`, ProblemDetail title `Tenant Not Allowed`), а не запись
    в чужой тенант. Легаси-поведение («взять ровно запрошенный тенант») возвращает
    `allow-tenant-override: true`.

    Важное следствие для `internal`-режима: там тенант владельца — `null`, поэтому любой
    непустой `tenantId` в запросе считается чужим и даёт 403. Подробнее —
    [Скоупы: тенант из запроса](scopes.md#тенант-из-запроса-allow-tenant-override).

---

## Готовый `SecurityFilterChain`

Статер регистрирует собственную цепочку для `/api/openapi/**` (бин
`openapiTokensSecurityFilterChain`, `@Order(1)`, создаётся только если такого бина ещё нет):

| Настройка | Значение |
|---|---|
| `securityMatcher` | `/api/openapi/**` |
| CSRF | **отключён** |
| `/api/openapi/admin/**` | требуется роль `ADMIN` (authority `ROLE_ADMIN`) |
| остальные `/api/openapi/**` | `authenticated()` |
| HTTP Basic | включён |
| Фильтр токенов | добавляется перед `UsernamePasswordAuthenticationFilter` |

Чтобы полностью контролировать правила, объявите свой бин с именем
`openapiTokensSecurityFilterChain` (или исключите автоконфигурацию) — тогда правила статера
не применятся. Подробнее: [Бэкенд-разработчику](../guides/backend.md).

---

## Авторизация в коде продукта

Скоупы токена превращаются в authority, поэтому работают стандартные механизмы Spring Security:

```java
@Configuration
@EnableMethodSecurity
class MethodSecurityConfig { }

@RestController
class PaymentsController {

    @GetMapping("/api/demo/payments")
    @PreAuthorize("hasAuthority('payments:read')")     // identity-режим
    public List<Payment> payments() { … }

    @PostMapping("/api/payments")
    @PreAuthorize("hasAuthority('createPayment')")     // mapped-режим
    public Payment create(@RequestBody PaymentRequest request) { … }
}
```

### Программные проверки

```java
@Autowired ApiTokenPermissionChecker checker;

if (checker.hasAuthority("readPayment")) { … }
checker.hasAnyAuthority("createPayment", "refundPayment");
```

`ApiTokenPermissionEvaluator` реализует и `PermissionEvaluator`, и `ApiTokenPermissionChecker`,
но регистрируется в контексте **как `ApiTokenPermissionChecker`**
(`@Bean ApiTokenPermissionChecker apiTokenPermissionChecker()`). Поэтому выражения
`hasPermission(...)` в `@PreAuthorize` **не заработают** без дополнительной настройки:
чтобы использовать `PermissionEvaluator`, зарегистрируйте его самостоятельно как бин
`PermissionEvaluator` и свяжите с `MethodSecurityExpressionHandler`.

---

## Повышение привилегий через скоупы

Исторический вектор атаки: `POST /api/openapi/tokens` принимал **любые** непустые строки
в `scopes`, и в режиме `scopes.mode=identity` скоуп `ROLE_ADMIN` становился authority,
открывающей `/api/openapi/admin/**` (`hasRole("ADMIN")`). Атакующий с обычной учётной
записью выпускал себе «админский» токен и получал доступ ко всем токенам.

Теперь этот сценарий закрыт **по умолчанию**:

| Слой защиты | Настройка по умолчанию | Что делает |
|---|---|---|
| Deny-шаблоны | `scopes.validation.deny-patterns: ["ROLE_.*"]` | отклоняет `ROLE_ADMIN` и любые роли (422) |
| Справочник | `scopes.validation.mode: catalog` | отклоняет скоупы, которых нет в `ScopeCatalog` |
| Проверка делегирования | `scopes.validation.mode: strict` (опционально) | запрещает выдать authority, которых нет у вызывающего |

Порядок и форма отказа — [Валидация скоупов](scopes.md#валидация-скоупов-при-создании-токена).
Итог: произвольный скоуп из запроса больше не создаётся молча — при нарушении правил
запрос создания падает с **422** и ничего не сохраняется.

!!! danger "Остаётся при `scopes.validation.mode: off` или переопределённом `ScopeValidator`"
    Режим `off` возвращает прежнее поведение — произвольные скоупы снова становятся
    authority. Не выключайте валидацию в продакшене и не заменяйте бин `ScopeValidator`
    на «принимающий всё» без собственной политики. Дополнительно ограничивайте
    административные эндпоинты статера отдельным контуром/ролью.

Подробнее об ограничениях — [Ограничения и roadmap](../operations/roadmap.md).

---

## Аудит аутентификации

Каждая попытка аутентификации по токену (успешная и неуспешная) записывается в аудит
с действием `AUTHENTICATE`. Событие формируется в `finally` **после** обработки запроса,
поэтому `responseStatus` — фактический статус ответа, а не всегда 200:

| Результат | `success` | `responseStatus` | Комментарий |
|---|---|---|---|
| Успех | `true` | фактический статус обработчика (200, 403, 404, 500, …) | пишется после `filterChain.doFilter` |
| Неверный токен/статус/TTL/пустые authority | `false` | 401 | `tokenId` отсутствует — токен не идентифицирован |
| Превышен пер-токенный rate limit | `false` | 429 | `tokenId` присутствует (токен опознан) |
| Превышен пре-аутентификационный лимит попыток | `false` | 429 | `tokenId` отсутствует — токен ещё не искали |

Записи о неуспешных попытках можно отключить —
`openapi.tokens.audit.record-failures=false`. Успешные аутентификации и события
жизненного цикла (`CREATE_TOKEN`, `REVOKE_TOKEN`, …) пишутся всегда, пока аудит включён.

Подробнее: [Аудит и статистика](audit.md).

## Связанные разделы

- [Rate limiting](rate-limiting.md) — политика лимитов на токен и пре-аутентификационный троттлинг.
- [Скоупы и права](scopes.md) — модели `identity` / `mapped` и валидация скоупов при создании.
- [Жизненный цикл токена](token-lifecycle.md) — TTL, квота и статусы.
- [Бэкенд-разработчику](../guides/backend.md) — интеграция и своя конфигурация безопасности.
- [Порты и SPI](../api/spi.md) — замена `TokenOwnerResolver`, `TokenHasher`, `ApiTokenAuthenticator`, `ScopeValidator`.
