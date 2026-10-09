# Документация для бэкенд-разработчика

Практическое руководство по встраиванию библиотеки в приложение: подключение, конфигурация,
настройка безопасности, расширение, работа с БД и типовые проблемы.

---

## 1. Что вы получаете

Одна зависимость `ru.openapi:openapi-tokens-spring-boot-starter` даёт:

| Что | Бин / класс |
|---|---|
| Домен и сервис жизненного цикла | `ApiTokenService` (транзакционный декоратор над `DefaultApiTokenService`) |
| Хранение | JPA-адаптеры + Flyway-миграции |
| Аутентификация по токену | `ApiTokenAuthenticationFilter`, `ApiTokenAuthenticationProvider`, `ApiTokenAuthentication` |
| Авторизация | authority из скоупов, `ApiTokenPermissionChecker` |
| REST | `UserApiTokenController`, `AdminApiTokenController`, `ScopeCatalogController`, `ApiTokenExceptionHandler` |
| Защита своих эндпоинтов | `SecurityFilterChain` для `/api/openapi/**` |
| Конфигурация | `OpenApiTokensProperties` (`openapi.tokens.*`), `AutoConfiguration.imports` |

## 2. Подключение

```kotlin
// build.gradle.kts
dependencies {
    implementation("ru.openapi:openapi-tokens-spring-boot-starter:0.1.0-SNAPSHOT")

    // нужны на стороне приложения:
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
```

Публикации в Maven-репозиторий нет — используйте `includeBuild("../open-api-lib")` или
локальный репозиторий (см. [Быстрый старт](../getting-started.md)).

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/openapi_tokens
    username: openapi
    password: openapi
  jpa:
    hibernate:
      ddl-auto: validate     # обязательно: схему создаёт Flyway
    open-in-view: false
  flyway:
    enabled: true

openapi:
  tokens:
    enabled: true
    auth:
      mode: internal
    scopes:
      mode: identity
```

Полный список свойств — [Конфигурация](../configuration.md).

---

## 3. Безопасность: три сценария

### 3.1 Оставить готовую цепочку статера

Ничего не делайте: бин `openapiTokensSecurityFilterChain` (`@Order(1)`) защитит
`/api/openapi/**` — Basic-аутентификация, фильтр токенов, `/api/openapi/admin/**` → `ROLE_ADMIN`,
CSRF отключён. Свою цепочку описывайте для остальных путей.

### 3.2 Полностью заменить правила

Объявите бин **с тем же именем** — автоконфигурация отступит
(`@ConditionalOnMissingBean(name = "openapiTokensSecurityFilterChain")`):

```java
@Bean
SecurityFilterChain openapiTokensSecurityFilterChain(
        HttpSecurity http,
        ApiTokenAuthenticationFilter apiTokenAuthenticationFilter
) throws Exception {
    return http
            .securityMatcher("/api/openapi/**")
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())                    // добавьте, если нужен SPA с другого origin
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/openapi/admin/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/openapi/tokens").hasAuthority("tokens:create")
                    .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}
```

### 3.3 Отключить безопасность статера

```yaml
spring:
  autoconfigure:
    exclude: ru.openapi.tokens.autoconfigure.OpenApiTokensAutoConfiguration
```

или `openapi.tokens.enabled=false` (отключит и функциональность тоже). Если нужно оставить
функциональность, но убрать только цепочку — используйте вариант 3.2.

!!! note "Почему провайдер аутентификации не публикуется как бин"
    В `OpenApiTokensAutoConfiguration` для фильтра создаётся **локальный** `ProviderManager`
    с `ApiTokenAuthenticationProvider`. Комментарий в коде объясняет: регистрация
    `AuthenticationProvider` бином заменила бы глобальный `DaoAuthenticationProvider`
    и сломала бы form-login/HTTP Basic. Учитывайте это, если будете собирать свой
    `AuthenticationManager`.

### 3.4 Защита собственных эндпоинтов

```java
@Configuration
@EnableMethodSecurity              // обязательно для @PreAuthorize
class MethodSecurityConfig { }

@RestController
class PaymentsController {

    @GetMapping("/api/payments")
    @PreAuthorize("hasAuthority('payments:read')")     // identity-режим
    public List<Payment> list() { … }

    @PostMapping("/api/payments")
    @PreAuthorize("hasAuthority('createPayment')")     // mapped-режим
    public Payment create(@Valid @RequestBody PaymentRequest request) { … }
}
```

Сервисный слой может проверить права программно:

```java
@Service
class RefundService {
    private final ApiTokenPermissionChecker permissions;

    RefundService(ApiTokenPermissionChecker permissions) {
        this.permissions = permissions;
    }

    void refund(UUID paymentId) {
        if (!permissions.hasAnyAuthority("refundPayment", "ROLE_ADMIN")) {
            throw new AccessDeniedException("Недостаточно прав для возврата");
        }
        …
    }
}
```

### 3.5 Обязательное усиление: ограничение скоупов

!!! danger "Не выводите эндпоинт создания токена «как есть» во внешний контур"
    `POST /api/openapi/tokens` принимает любые непустые скоупы. В режиме `identity`
    пользователь может выпустить токен со скоупом `ROLE_ADMIN` и получить административный
    доступ. Варианты защиты:

    === "Валидация скоупов"

        ```java
        @RestController
        @RequestMapping("/api/tokens")
        class SafeTokenController {

            private final ApiTokenService service;
            private final ScopeCatalog catalog;

            @PostMapping
            public CreatedTokenResponse create(@Valid @RequestBody CreateTokenRequest request,
                                               Authentication authentication) {
                Set<String> allowed = catalog.listScopes().stream()
                        .map(ScopeInfo::scope)
                        .collect(Collectors.toSet());

                List<String> unknown = request.scopes().stream()
                        .filter(scope -> !allowed.contains(scope))
                        .toList();
                if (!unknown.isEmpty()) {
                    throw new IllegalArgumentException("Неизвестные скоупы: " + unknown);
                }

                Set<String> userAuthorities = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                if (!userAuthorities.containsAll(request.scopes())) {
                    throw new AccessDeniedException("Нельзя выдать права, которых у вас нет");
                }

                return mapper.toCreatedResponse(service.create(mapper.toCommand(request)));
            }
        }
        ```

    === "Режим mapped"

        ```yaml
        openapi:
          tokens:
            scopes:
              mode: mapped
              mappings:
                payments:
                  - payments:read
                  - payments:write
        ```

        Тогда authority появляются только из объявленных маппингов, и произвольная строка
        в скоупе не даёт прав.

    === "Отдельный контур"

        Не подключайте `openapi-tokens-web` к публичному API: используйте только
        `ApiTokenService` в своих контроллерах с собственной авторизацией.

### 3.6 Режим `keycloak` (OIDC и resource server)

Если пользователи и клиенты аутентифицируются в Keycloak, режим `internal` не подойдёт:
владелец токена определяется из JWT (`sub`), а готовая цепочка статера принимает только
HTTP Basic и JWT не пропустит. Нужно объявить свою цепочку с тем же именем бина,
добавив в неё resource server и фильтр API-токенов:

```java
@Bean
@Order(1)
SecurityFilterChain openapiTokensSecurityFilterChain(
        HttpSecurity http,
        ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
        Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakJwtAuthenticationConverter
) throws Exception {
    return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(rs -> rs.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
            .securityMatcher("/api/openapi/**")
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/openapi/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}
```

Плюс конфигурация:

```yaml
openapi:
  tokens:
    auth:
      mode: keycloak
      keycloak:
        tenant-claim: tenant_id

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/my-realm
```

Два обязательных условия, кроме самой цепочки:

1. **Маппинг ролей Keycloak в `ROLE_*`** (`realm_access.roles` → `SimpleGrantedAuthority("ROLE_ADMIN")`)
   для JWT и для интерактивного входа (`GrantedAuthoritiesMapper`) — иначе `hasRole("ADMIN")`
   и админский UI не работают.
2. **Claim тенанта** в токене (или свой `tenant-claim`), иначе `tenantId` у токенов будет `null`.

Полный работающий пример (три цепочки, импорт realm, маппинг ролей, тесты без живого Keycloak) —
[Keycloak: интеграция](keycloak.md).

---

## 4. Расширение и замена компонентов

Любой порт заменяется своим бином (`@ConditionalOnMissingBean`):

```java
@Configuration
class TokenCustomizations {

    /** Свой владелец — например, из контекста тенанта. */
    @Bean
    TokenOwnerResolver tokenOwnerResolver(TenantContext context) {
        return () -> new TokenOwner(context.userId(), context.tenantId());
    }

    /** BCrypt вместо Argon2id по умолчанию (или свои параметры). */
    @Bean
    TokenHasher tokenHasher() {
        return new BcryptTokenHasher();
    }

    /** Распределённый лимитер. */
    @Bean
    RateLimiter rateLimiter(RedisTemplate<String, String> redis) {
        return new RedisRateLimiter(redis);
    }

    /** Фиксированное время (детерминированные тесты). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

Полный список портов и условий — [Порты и SPI](../api/spi.md).

### Что нельзя расширить

| Ограничение | Обходной путь |
|---|---|
| Формат токена и regex | Свой `ApiTokenAuthenticator` + своя схема хранения |
| Поля REST-DTO | Свой контроллер/маппер поверх `ApiTokenService` |
| Миграции Flyway (имена и версии) | Свои миграции поверх; либо переименовать файлы библиотеки до первого применения |
| Схема таблиц | Свои миграции + свой `ApiTokenRepository` |
| Логика проверок в аутентификаторе | Свой `ApiTokenAuthenticator` |

---

## 5. Транзакции

```
ApiTokenService (бин)
   └── TransactionalApiTokenService   @Transactional(readOnly = true) на классе
          ├── create / revoke / block / forceRevoke / forceBlock / touchLastUsed → @Transactional
          └── get / listOwn / usageStats / listAll / getAny → readOnly
   └── DefaultApiTokenService (ядро, без Spring)
```

Практические следствия:

- Ядро не зависит от Spring — если создавать `DefaultApiTokenService` вручную (как в тестах),
  транзакции на нём не будет.
- Чтение списков возвращает доменные объекты, полностью материализованные внутри транзакции
  (ленивые скоупы инициализируются в адаптере).
- `spring.jpa.open-in-view=false` — правильная настройка; не включайте обратно, полагаясь на
  ленивую загрузку в представлении.

---

## 6. Работа с БД

### Миграции

Модуль `openapi-tokens-persistence-jpa` — единственное место в репозитории с `db/migration`,
поэтому Flyway подхватит `V1__api_tokens.sql` и `V2__scope_catalog.sql` из jar.

!!! warning "Версии миграций глобальны"
    Если в вашем приложении уже есть свои `V1`/`V2`, конфликт неизбежен. Варианты:

    1. переименовать файлы библиотеки в исходниках до первого применения (например, `V100__…`);
    2. изолировать историю библиотеки: `spring.flyway.table` (тогда таблицы библиотеки
       всё равно создаются в той же схеме — изоляция только по истории версий);
    3. исключить `OpenApiTokensPersistenceAutoConfiguration` и написать свои миграции
       с теми же таблицами.

### Запросы из своего кода

Используйте порты: `ApiTokenRepository`, `AuditLogRepository`, `ScopeCatalogRepository`,
`ScopeMappingRepository`. JPA-сущности (`ru.openapi.tokens.persistence.internal.*`)
package-private — работать с ними напрямую нельзя и не нужно.

Известные особенности SQL-слоя (учитывайте при нагрузке и при написании своих запросов):

| Особенность | Последствие |
|---|---|
| `findAll()` инициализирует скоупы построчно | N+1 при большом числе токенов (админский список) |
| `findById` делает дополнительный SELECT скоупов | +1 запрос на обращение |
| `findByPrefix`/`findByOwnerId` используют `@EntityGraph` | Один запрос с join (но не годится для пагинации по коллекции) |
| `findByTokenId(tokenId, offset, limit)` считает `page = offset / limit` | Некратный offset игнорируется; `limit <= 0` → `page=0, size=20` |
| Уникальность `scope_mapping` не защищает от NULL-дублей | Повторный `save` создаёт новую строку |
| Нет `@Version` | Конкурентные изменения — «последний победил» |
| Нет FK у `api_token_audit_log.token_id` | Аудит переживает удаление токена, возможны «сироты» |

Подробно — [Модель данных](../architecture/data-model.md).

### Своя реализация хранилища

```java
@Bean
ApiTokenRepository apiTokenRepository(MongoTemplate mongo) {
    return new MongoApiTokenRepository(mongo);
}
```

Порт `ApiTokenRepository`: `save`, `findById`, `findByPrefix`, `findByOwnerId`, `findAll`,
`countByOwnerId`, `deleteById`. Порт должен возвращать доменные `ApiToken` с заполненными
скоупами и `TokenExpiry` (включая `createdAt` — он нужен для расчёта скользящего TTL).

---

## 7. Особенности, о которых нужно знать

| # | Особенность | Что делать |
|---|---|---|
| 1 | Свойства `audit.enabled/async/record-failures`, `rate-limit.backend`, `token.default-ttl` **не читаются** | Не полагайтесь на них; отключение аудита — своим бином `AuditRecorder` |
| 2 | `ApiTokenService#listAll/getAny/forceRevoke/forceBlock` не проверяют права | Защищайте свои эндпоинты `@PreAuthorize` |
| 3 | Скоупы из запроса не валидируются | Обязательное усиление — см. раздел 3.5 |
| 4 | `tenantId` берётся из запроса без сверки с владельцем | Убирайте поле или перезаписывайте из контекста |
| 5 | `ApiTokenPermissionEvaluator` зарегистрирован как `ApiTokenPermissionChecker` | Для `hasPermission(...)` в SpEL нужен свой бин `PermissionEvaluator` + `MethodSecurityExpressionHandler` |
| 6 | Просроченные токены остаются `ACTIVE` в БД | Не опирайтесь на статус — проверяйте `expiresAt`/`lastUsedAt` |
| 7 | `DELETE` не идемпотентен (повтор → 400) | Учтите в ретраях |
| 8 | Ошибки 401/429 от фильтра — не JSON | Настройте свой `AuthenticationEntryPoint`, если нужен единый формат |
| 9 | `touchLastUsed` — UPDATE при каждом успешном запросе | При высокой нагрузке рассмотрите кеш/отложенную запись |
| 10 | Argon2id проверяется на каждом запросе (64 МиБ, 3 итерации) | Оцените стоимость; при необходимости снизьте параметры своим `TokenHasher` |
| 11 | Ведра rate limiter никогда не удаляются | Свой `RateLimiter` с TTL/кешем |
| 12 | В кластере лимит умножается на число узлов | Внешний лимитер |
| 13 | Аудит растёт неограниченно | Регламент очистки/партиционирование |
| 14 | `endpoint`/`user_agent` ограничены 512 символами | Ошибка вставки логируется; при длинных URL маскируйте значения своим `AuditRecorder` |

---

## 8. Логирование и диагностика

| Событие | Где смотреть |
|---|---|
| Ошибки записи аудита | лог `ERROR` `Failed to append audit event for token {}` (логгер `AsyncAuditRecorder`) |
| Логи библиотеки | задайте `logging.level.ru.openapi.tokens=DEBUG` для подробностей |
| Отказ аутентификации | HTTP 401 без деталей; смотрите аудит-журнал по токену |
| Превышение лимита | HTTP 429 + запись в аудите с `responseStatus=429` |
| Некорректный запрос | `ProblemDetail` в ответе; для валидации — поле `detail` |

Полезно добавить своим кодом (библиотека их не отдаёт):

```java
// метрики на бизнес-события
meterRegistry.counter("api_tokens.created", "owner", ownerId).increment();
```

---

## 9. Тестирование интеграции

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TokenIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("openapi_tokens")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @Test
    void issuesTokenAndUsesIt(@Autowired MockMvc mvc) throws Exception {
        String raw = …; // POST /api/openapi/tokens с httpBasic(...)
        mvc.perform(get("/api/your-endpoint").header("Authorization", "Bearer " + raw))
           .andExpect(status().isOk());
    }
}
```

Рекомендации:

- используйте `Testcontainers` с PostgreSQL (не H2 — схема и типы отличаются);
- для юнит-уровня библиотеки достаточно `@WebMvcTest` + `@MockitoBean`
  (`ApiTokenService`, `AuditLogRepository`, `ScopeCatalog`);
- для проверки «выключенного» статера — `ApplicationContextRunner` с
  `openapi.tokens.enabled=false`;
- фиксируйте время своим бином `Clock`, если проверяете TTL.

---

## 10. Соглашения проекта

Код в репозитории следует правилам `.cursor/rules/java-senior.mdc`. Ключевое для контрибьютора:

| Правило | Как соблюдено |
|---|---|
| Конструкторная инъекция, без `@Autowired` на полях | ✅ везде |
| DTO/records в REST, никаких сущностей в ответах | ✅ `openapi-tokens-web/dto` |
| Изоляция `dto` ↔ `@Entity` + ArchUnit-тест | ✅ `ArchitectureTest` |
| `FetchType.LAZY` для коллекций | ✅ `@ElementCollection(fetch = LAZY)` + `@EntityGraph` |
| `@Transactional(readOnly = true)` на сервисе | ✅ `TransactionalApiTokenService` |
| AssertJ в тестах, `@ExtendWith(MockitoExtension.class)` | ✅ |
| BDD (Gherkin) для бизнес-требований | ✅ `features/scopes`, `features/token` |
| Метод ≤ 30 строк, ≤ 4 параметров, guard clauses | ✅ в основном |
| `Object.requireNonNull` / `orElseThrow` вместо `Optional#get` | ✅ |
| Logging через плейсхолдеры `{}` | ✅ (`AsyncAuditRecorder`) |

Тестовые инструменты: JUnit 5, AssertJ, Mockito, Cucumber 7.20.1, Testcontainers 1.20.4,
ArchUnit 1.3.0. Отдельной задачи `integrationTest` нет — все тесты (включая `*IT`) идут
под `./gradlew test`.

---

## 11. Чек-лист код-ревью интеграции

- [ ] `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`.
- [ ] Версии миграций библиотеки не конфликтуют с миграциями продукта.
- [ ] Настроена аутентификация пользователей и `@EnableMethodSecurity`.
- [ ] **Ограничен набор скоупов** при создании токена (или используется режим `mapped`).
- [ ] `tenantId` не принимается «как есть» от клиента (или принимается осознанно).
- [ ] Административные эндпоинты защищены (роль/authority), а не только URL-паттерном.
- [ ] Настроен CORS, если UI на другом origin.
- [ ] Есть регламент очистки `api_token_audit_log`.
- [ ] Оценена стоимость хеширования на горячем пути (Argon2id).
- [ ] Решён вопрос с распределённым rate limit (если приложение в кластере).
- [ ] Есть мониторинг ошибок записи аудита и размера журнала.
- [ ] Не используются «мёртвые» свойства (`audit.*`, `rate-limit.backend`, `token.default-ttl`).

---

## 12. Связанные разделы

- [Конфигурация](../configuration.md) — все свойства и их реальное влияние.
- [Порты и SPI](../api/spi.md) — замена компонентов.
- [Java API](../api/java-api.md) — сервис, домен, исключения.
- [Модель данных](../architecture/data-model.md) — схема и особенности SQL.
- [Тестирование](../operations/testing.md) — как запускать тесты проекта.
- [Ограничения и roadmap](../operations/roadmap.md) — известные дефекты и планы.
