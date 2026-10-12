# Валидация проекта и план харденинга

## Overview

- **Что это.** Независимая проверка репозитория `open-api-lib` (библиотека API-токенов на Spring Boot):
  сборка, тесты, безопасность, персистентность, REST-контракт, конфигурация, документация.
- **Зачем.** Зафиксировать подтверждённые дефекты с доказательствами, отделить их от уже
  известных ограничений (см. [Ограничения и roadmap](../operations/roadmap.md)) и дать
  пошаговый план исправлений.
- **Ключевой пункт заказчика** — валидация скоупов при создании токена (раздел
  [«Валидация скоупов — целевой дизайн»](#8-валидация-скоупов--целевой-дизайн)).
- **Скоуп.** Ничего не менялось: это отчёт + план. Код не правлен.

## 0. Статус реализации (обновлено 2026-10-09)

План реализован. Ниже — фактический результат; разделы 1–10 описывают исходное состояние
аудита и остаются как история.

| Область | Статус |
|---|---|
| C-1 валидация скоупов | ✅ реализована: SPI `ScopeValidator`, режимы `off/deny-list/catalog/strict` (default `catalog`), deny-pattern `ROLE_.*`, отказ 422 до списания квоты и генерации секрета |
| C-2 тенант из запроса | ✅ `scopes.allow-tenant-override` (default `false`) + 403 |
| H-1 «воскрешение» токена | ✅ `@Version` (миграция V3) + точечный `UPDATE ... WHERE status='ACTIVE'` для `touchLastUsed` |
| H-2 `persist` вместо merge | ✅ `@Version` делает новые сущности `isNew` → INSERT без SELECT-пробы |
| H-3 гонка квоты | ✅ `pg_advisory_xact_lock` на владельца (`ApiTokenRepository.lockOwnerQuota`); квота считается по ACTIVE+BLOCKED |
| H-4 N+1 резолвинга скоупов | ✅ батчевый `ScopeMappingSource.projectScopesFor(Set, tenantId)`; один запрос на запрос |
| H-5 невалидированные лимиты | ✅ `@Positive/@Max` + «оба или ни одного» + валидация в `RateLimitPolicy` |
| H-6 переполнение колонок | ✅ `@Size(max=128)` на `tenantId` и элементы скоупов, `@Positive @Max` на TTL |
| H-7 CI | ✅ job `test` в `.gitlab-ci.yml` (temurin 21 + docker:dind) и `.github/workflows/build.yml` |
| H-8 перебор после хеша | ✅ пред-аутентификационный лимит по `auth-fail:<prefix>` (`auth.failed-attempt-*`) |
| H-9 оракул префикса | ✅ длина префикса ≥ 8 по умолчанию остаётся; лимит неуспешных попыток + единый 401 |
| M-1 глобальный фильтр | ✅ `FilterRegistrationBean#setEnabled(false)` |
| M-2/M-3 ошибки REST | ✅ advice сужен до `ru.openapi.tokens.web`, добавлены 400/403/409/422 и ProblemDetail-поля |
| M-4 нет `@Version` | ✅ см. H-1 |
| M-5 defense in depth | ✅ `@PreAuthorize("hasRole('ADMIN')")` на админском контроллере |
| M-6 пагинация админки | ✅ `offset/limit` + `X-Total-Count`, `findPage` без N+1 |
| M-7/M-8/M-9 аудит | ✅ обрезка полей, ограниченная очередь + CallerRunsPolicy, статус после обработчика, `tokenId` при 429, события жизненного цикла, `audit.enabled/async/record-failures` работают |
| M-10 пагинация аудита | ✅ истинный offset/limit, clamp 500 |
| M-11 дубли `scope_mapping` | ✅ `UNIQUE NULLS NOT DISTINCT` + дедупликация (V3) |
| M-12 N+1 `findAll`/`findById` | ✅ `@EntityGraph` на переопределённых методах |
| M-13 неизвестные JSON-поля | ✅ `@JsonAnySetter` → 400 |
| M-14 `expiresAt` в прошлом | ✅ `@Future` |
| M-15 кеширование секрета | ✅ `Cache-Control: no-store` |
| M-16 идемпотентный DELETE | ✅ 204 при повторном отзыве |
| M-17 DTO | ✅ добавлены `effectiveStatus`, `slidingTtlSeconds`, параметры лимита, `createdBy` |
| M-18 `scopes.mode` строкой | ✅ enum `ScopeMode` — опечатка падает при старте |
| M-19/M-28 порядок автоконфигураций | ✅ `@AutoConfiguration(after=...)` для security/persistence |
| M-20 ArchUnit вхолостую | ✅ перенесён в `openapi-tokens-autoconfigure`, 4 правила, без `allowEmptyShould` |
| M-21 тесты безопасности | ✅ фильтр, провайдер (частично), цепочка, e2e-эскалация — покрыто |
| M-22/M-23 качество сборки | ✅ JaCoCo + пороги, `-Xlint:all -Werror`, `javadoc failOnError`, `junit-platform-launcher` |
| M-24 default TTL | ✅ `token.default-ttl` применяется |
| M-25 PermissionEvaluator | ✅ бин `PermissionEvaluator` + `hasPermission` учитывает переданную аутентификацию |
| M-26 анонимный владелец | ✅ `AnonymousAuthenticationToken` отклоняется |
| M-27 смена алгоритма хеша | ✅ тегированные хеши `{argon2}`/`{bcrypt}` + распознавание legacy |
| M-29 гонка `touchLastUsed` | ✅ точечный UPDATE + 401 вместо 500 |
| M-30 пустой набор authority | ✅ `auth.reject-empty-authorities` (default true) |
| M-31 fail-open `TokenExpiry` | ✅ fail-closed без якоря |
| M-32 индекс `scope_mapping` | ✅ `idx_scope_mapping_token_scope_tenant` (V3) |
| L-1/L-2/L-3/L-5/L-6/L-7/L-9/L-10/L-15 | ✅ исправлено (индексы, комментарий, зависимости, `WebTestApplication`, `maven-publish`, javadoc/sources только для библиотек, `distributionSha256Sum`, `.editorconfig`) |
| L-17/L-18/L-19 | ✅ eviction ведёр, `WWW-Authenticate`/`Retry-After`, пред-аутентификационный лимит |
| L-4/L-8/L-11/L-12/L-13/L-14/L-16 | ⏳ остались (переполнение TTL, springdoc, дубли зависимостей в сэмплах, дев-креды, `amplicode.xml`, метрики) |

**Осталось (осознанно отложено):** Redis-`RateLimiter` (`rate-limit.backend` всё ещё не читается),
Micrometer-метрики, springdoc/OpenAPI, партиционирование и retention аудита, version catalog,
Spotless/Checkstyle, dependency locking, маскирование PII.

**Проверка результата:** `./gradlew clean build` — BUILD SUCCESSFUL; **229 тестов, 0 падений**;
покрытие строк: core 84.2 %, security 76.1 %, persistence 90.6 %, autoconfigure 67.4 %, web 50.7 %;
`mkdocs build --strict` — OK.

## 1. Базовая линия проверки (что реально запускалось)

| Проверка | Команда | Результат |
|---|---|---|
| Компиляция, jar, javadoc, тесты | `gradle build --continue --offline` | BUILD SUCCESSFUL |
| Полный прогон тестов (принудительно, без кеша) | `gradle test --rerun-tasks --offline` | BUILD SUCCESSFUL, **160 тестов, 0 падений**, 18 с |
| Javadoc | часть `build` | 1 warning (`UiSecurityConfig` reference not found) + ошибка в модуле `spring-boot-starter` (`No public or protected classes found`), **не валит сборку** из-за `isFailOnError=false` |
| Gradle 9-совместимость | отчёт `build/reports/problems` | WARNING: `The automatic loading of test framework implementation dependencies has been deprecated` |
| Security-цепочки (runtime-проба) | временный `@SpringBootTest` в `openapi-tokens-sample` | `GET /api/openapi/admin/tokens`: `demo` → **403**, `admin` → **200**, аноним → **401**; `GET /` → 302 на `/login` — цепочки `@Order` работают корректно |
| **Эскалация привилегий (runtime-проба)** | то же | токен со скоупом `ROLE_ADMIN`, выпущенный `demo`, открывает `GET /api/openapi/admin/tokens` → **200** (C-1 подтверждён) |
| **Подмена тенанта (runtime-проба)** | то же | `POST /api/openapi/tokens` с `tenantId: tenant-a` → **201**, токен сохранён с чужим тенантом (C-2 подтверждён) |
| Поведение Bucket4j 8.14.0 | изолированная проба | `capacity(0/−5)` и `period 0/−1` → `IllegalArgumentException` |
| Регистрация фильтра | runtime-проба | `ApiTokenAuthenticationFilter` зарегистрирован контейнером как **глобальный** сервлет-фильтр (`urls=[[]]`, order `Integer.MAX_VALUE`) |

**Вывод:** проект собирается и все тесты зелёные. Проверка (в т.ч. `spring-boot-starter`) идёт
с workspace-локальным `GRADLE_USER_HOME` + `GRADLE_RO_DEP_CACHE`, потому что sandbox не даёт
писать в `~/.gradle`; для CI это несущественно.

## 2. Итог одной строкой

Архитектура и документирование — выше среднего (чистое разделение модулей, ядро без Spring,
160 тестов, документация честно перечисляет свои дыры). Основные риски сосредоточены в
**отсутствии валидации входных данных на создании токена** (скоупы, тенант, лимиты),
**отсутствии оптимистичных блокировок** в персистентности и в том, что **CI не собирает и не
тестирует проект вообще**.

## 3. Критично

### C-1. Валидация скоупов отсутствует → повышение привилегий

- **Факт.** `CreateTokenRequest` ограничивает скоупы только `@NotEmpty Set<@NotBlank String>`
  ([CreateTokenRequest.java:13](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/dto/CreateTokenRequest.java#L13)).
  `IdentityScopeResolver` превращает любую строку в authority как есть
  ([IdentityScopeResolver.java:16-19](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/scope/IdentityScopeResolver.java#L16-L19)).
  Готовая цепочка требует `hasRole("ADMIN")` для `/api/openapi/admin/**`
  ([OpenApiTokensAutoConfiguration.java:255](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensAutoConfiguration.java#L255)),
  а `ScopeCatalog` при создании токена не читается вообще.
- **Сценарий.** Пользователь `demo` выпускает токен со скоупом `ROLE_ADMIN` и получает полный
  доступ к `/api/openapi/admin/**` (список/блокировка/отзыв чужих токенов, чтение аудита с IP и UA).
  В `mapped`-режиме аналогично можно запросить объявленный «админский» скоуп (`admin:all` в
  Keycloak-семпле).
- **Статус.** Известно и задокументировано ([security.md](../features/security.md),
  [scopes.md](../features/scopes.md), roadmap 1.1), но **не исправлено**.
- **Подтверждено end-to-end** (временный `@SpringBootTest` в `openapi-tokens-sample`):
  `POST /api/openapi/tokens` от `demo`/`demo` со `{"scopes":["ROLE_ADMIN"]}` → **201**;
  следующий `GET /api/openapi/admin/tokens` с этим токеном → **200** (без эскалации тот же
  запрос по Basic даёт 403). То есть пользователь без прав администратора читает все токены.
- **Решение.** Плучаемый `ScopeValidator` + fail-closed режим по умолчанию — см. раздел 8.

### C-2. Тенант берётся из запроса без проверки → межтенантное повышение прав

- **Факт.** `command.tenantId() != null ? command.tenantId() : owner.tenantId()`
  ([DefaultApiTokenService.java:88](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/service/DefaultApiTokenService.java#L88)).
  Тенант далее попадает в `ScopeResolver` и в JPQL
  `WHERE m.tenantId IS NULL OR m.tenantId = :tenantId`
  ([ScopeMappingJpaRepository.java:14-19](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/ScopeMappingJpaRepository.java#L14-L19)).
- **Сценарий.** Пользователь тенанта B создаёт токен с `"tenantId":"tenant-a"` и получает
  authority, объявленные только для тенанта A (в `mapped`-режиме). Также это «запись токена в чужой тенант».
- **Подтверждено end-to-end:** `POST /api/openapi/tokens` с `"tenantId":"tenant-a"` от `demo` → **201**,
  в ответе `"tenantId":"tenant-a"` (владелец — `demo` без тенанта).
- **Решение.** Свойство `openapi.tokens.scopes.allow-tenant-override` (default `false`):
  игнорировать поле из запроса либо требовать равенства с `owner.tenantId()`; для сервисных
  сценариев — разрешать только «привилегированному» вызывающему.

## 4. Высокий приоритет

### H-1. Нет оптимистичных блокировок: отозванный токен может «воскреснуть»

- **Факт.** `ApiTokenEntity` без `@Version`, `JpaApiTokenRepository.save` перезаписывает **все**
  поля из доменного снимка
  ([ApiTokenEntityMapper.java:15-35](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/ApiTokenEntityMapper.java#L15-L35),
  [JpaApiTokenRepository.java:30-35](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/JpaApiTokenRepository.java#L30-L35)).
- **Сценарий (гонка).** `touchLastUsed` читает токен `ACTIVE`
  ([DefaultApiTokenService.java:165-172](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/service/DefaultApiTokenService.java#L165-L172)),
  параллельно админ коммитит `revoke()`; merge записывает устаревший снимок →
  `status='ACTIVE', revoked_at=NULL`. Отзыв молча отменяется, токен продолжает работать.
  Симметрично `forceBlock`/`unblock` могут перетереть друг друга.
- **Решение.** (1) точечный `@Modifying @Query("update ApiTokenEntity t set t.lastUsedAt=:now where t.id=:id and t.status=ACTIVE")`
  для горячего пути; (2) `@Version` + миграция `ALTER TABLE api_token ADD COLUMN version BIGINT NOT NULL DEFAULT 0`;
  (3) `@Lock(PESSIMISTIC_WRITE)` на чтение перед сменой статуса.

### H-2. `save()` никогда не делает `persist()` — всегда SELECT-проба + merge

- **Факт.** `@Id` присваивается вручную
  ([ApiTokenEntityMapper.java:17](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/ApiTokenEntityMapper.java#L17)),
  `@Version` нет → `isNew()` всегда `false`. Подтверждено логом Hibernate из
  `build/test-results/test/TEST-...PersistenceIntegrationTest.xml`:
  `select ... from api_token where id=?`, затем `insert into api_token (...)`.
- **Влияние.** +1 SELECT на создание, +1–2 на обновление (`findById → save` = 4 запроса),
  `save()` возвращает копию, а не сохранённый инстанс.
- **Решение.** `implements Persistable<UUID>` с transient-флагом, либо `@Version`
  (null ⇒ `isNew=true` ⇒ `persist`), либо отдельный `insert` в порту.

### H-3. Гонка квоты (TOCTOU)

- **Факт.** `countByOwnerId(...) >= maxTokensPerOwner` затем вставка
  ([DefaultApiTokenService.java:75-78](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/service/DefaultApiTokenService.java#L75-L78)).
  Два параллельных `POST /tokens` при count = max−1 оба проходят → квота превышена навсегда.
- **Решение.** Атомарный счётчик (`UPDATE ... WHERE tokens < :max` с проверкой rowsAffected),
  либо `pg_advisory_xact_lock(hashtext(ownerId))` в начале `create`, либо `SELECT ... FOR UPDATE`
  на якорной строке владельца.

### H-4. N+1 на горячем пути аутентификации

- **Факт.** `MappedScopeResolver` вызывает `projectScopesFor(scope, tenantId)` **по одному скоупу**
  ([MappedScopeResolver.java:25-27](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/scope/MappedScopeResolver.java#L25-L27)),
  а `CompositeMappingSource` делает отдельный запрос на каждый скоуп
  ([CompositeMappingSource.java:36](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/scope/CompositeMappingSource.java#L36)) —
  хотя порт батчевый: `findByTokenScopes(Iterable<String>, String)`.
  Тот же паттерн в `DefaultScopeCatalog.listScopes()`
  ([DefaultScopeCatalog.java:59](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/scope/DefaultScopeCatalog.java#L59)).
- **Влияние.** Токен с 6 скоупами → 6 SELECT + 6 транзакций на каждый запрос к API.
- **Решение.** Добавить в `ScopeMappingSource` батчевый метод, возвращающий `Map<String, Set<String>>`,
  и один раз звать `findByTokenScopes(tokenScopes, tenantId)`.

### H-5. Невалидированные лимиты → «навсегда сломанный» токен и HTTP 500

- **Факт.** `rateLimitRequests` / `rateLimitWindowSeconds` в DTO без ограничений
  ([CreateTokenRequest.java:16-17](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/dto/CreateTokenRequest.java#L16-L17));
  значения доходят до `Bandwidth.capacity(...)`/`refillGreedy(...)`
  ([Bucket4jRateLimiter.java:43-46](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/ratelimit/Bucket4jRateLimiter.java#L43-L46)),
  а ведро строится **лениво, внутри фильтра**, который ловит только
  `RateLimitExceededException`/`AuthenticationException`
  ([ApiTokenAuthenticationFilter.java:69-75](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/ApiTokenAuthenticationFilter.java#L69-L75)).
- **Подтверждено runtime-пробой:** `POST /api/openapi/tokens` с `rateLimitRequests=0, window=60`
  → **201**, а первый же запрос с этим токеном → `IllegalArgumentException: 0 is wrong value for capacity`
  (в контейнере — 500, без ProblemDetail и без записи в аудит).
- **Решение.** `@Positive` + `@Max` на оба поля и кросс-полевая проверка «либо оба, либо ни одного»
  (сейчас половина лимита молча означает «без лимита» —
  [RateLimitPolicy.java:12-14](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/RateLimitPolicy.java#L12-L14)).
  Дополнительно: оборачивать построение ведра в доменную ошибку конфигурации токена.

### H-6. Переполнение колонок → 500 и «мусорные» данные

- **Факт.** `tenantId` без `@Size` против `VARCHAR(128)`
  ([CreateTokenRequest.java:18](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/dto/CreateTokenRequest.java#L18),
  [V1__api_tokens.sql:8](../../openapi-tokens-persistence-jpa/src/main/resources/db/migration/V1__api_tokens.sql#L8));
  элементы `scopes` без ограничения длины против `api_token_scope.scope VARCHAR(128)`
  ([V1__api_tokens.sql:27](../../openapi-tokens-persistence-jpa/src/main/resources/db/migration/V1__api_tokens.sql#L27)).
  `name`/`description` при этом ограничены — несогласованность.
- **Сценарий.** `tenantId` из 129 символов или скоуп из 200 символов → `DataIntegrityViolationException`
  без обработчика → 500.
- **Решение.** `@Size(max=128)` на `tenantId` и элементы множества скоупов (+ лимит числа скоупов).

### H-7. CI не собирает и не тестирует проект

- **Факт.** `.gitlab-ci.yml` объявляет стадию `test`, но job'а нет — только `pages`
  ([.gitlab-ci.yml:7-16](../../.gitlab-ci.yml#L7-L16)); единственный GitHub workflow — docs-only
  ([.github/workflows/docs.yml](../../.github/workflows/docs.yml)). Docker/dind нигде не поднимается,
  хотя интеграционные тесты используют Testcontainers.
- **Решение.** Job `test` на `eclipse-temurin:21-jdk` + `docker:dind`, `./gradlew test --no-daemon`,
  артефакты `*/build/test-results/test/*.xml`; `SITE_URL: "$CI_PAGES_URL/"` для pages-job
  (сейчас абсолютные URL на GitLab Pages указывают на `https://example.com/open-api-lib/`).

### H-8. Rate limit проверяется **после** проверки хеша → неограниченный перебор и DoS

- **Факт.** `DefaultApiTokenAuthenticator` сначала делает `tokenHasher.matches(...)`, и только потом
  `rateLimiter.tryAcquire(...)`
  ([DefaultApiTokenAuthenticator.java:50-66](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/DefaultApiTokenAuthenticator.java#L50-L66)).
  Неизвестный префикс выходит ещё раньше, а `RateLimitPolicy.isConfigured()` по умолчанию `false`.
- **Сценарий.** Зная/подобрав префикс, атакующий шлёт неограниченный поток запросов с неверными
  секретами: каждый запрос = один `argon2id verify` (64 МиБ, 3 итерации) + чтение БД. Отказы
  ничем не лимитируются (ни по префиксу, ни по IP).
- **Решение.** Пред-аутентификационный лимитер по `prefix`+IP **до** `matches`, лимитировать и
  неуспешные попытки; кешировать результат проверки токена.

### H-9. Оракул существования префикса (тайминг)

- **Факт.** Неизвестный префикс → мгновенный `Optional.empty()`; известный префикс с неверным
  секретом → дорогой Argon2. `isUsable`/`isExpired` также проверяются до хеширования
  ([DefaultApiTokenAuthenticator.java:50-63](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/DefaultApiTokenAuthenticator.java#L50-L63)).
  Текст ответа одинаковый (401), но стоимость разная.
- **Влияние.** Возможна перечислимость валидных префиксов (основа для H-8), а при `prefix-length: 4`
  пространство префиксов — 65 536 значений.
- **Решение.** Постоянная работа на отказ (один dummy-verify), единый ответ, `prefix-length >= 8`
  по умолчанию и предупреждение/запрет 4–6; префиксы не логировать открытым текстом.

## 5. Средний приоритет

| # | Проблема | Где | Решение |
|---|---|---|---|
| M-1 | Фильтр токенов зарегистрирован как **глобальный** сервлет-фильтр для всех URL (подтверждено runtime-пробой), хотя документирован как часть цепочки `/api/openapi/**` | [OpenApiTokensAutoConfiguration.java:222-241](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensAutoConfiguration.java#L222-L241) | добавить `FilterRegistrationBean<ApiTokenAuthenticationFilter>` с `setEnabled(false)` |
| M-2 | `@RestControllerAdvice` без `basePackages`/`assignableTypes` — перехватывает **все** контроллеры приложения, включая чужие `IllegalArgumentException` → 400 «Invalid Token Request» | [ApiTokenExceptionHandler.java:12-35](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/ApiTokenExceptionHandler.java#L12-L35) | сузить до `ru.openapi.tokens.web` или своих типов; `IllegalArgumentException` → 500 |
| M-3 | Нет обработчиков `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `ConstraintViolationException`, `DataIntegrityViolationException` → ответы не в формате ProblemDetail | `ApiTokenExceptionHandler` | добавить обработчики (или наследоваться от `ResponseEntityExceptionHandler`) |
| M-4 | Нет `@Version`/ETag → «последний победил» (см. H-1) | `ApiTokenEntity` | `@Version` + `If-Match` |
| M-5 | Админский REST без defense-in-depth: `AdminApiTokenController` без `@PreAuthorize`, авторизация целиком на цепочке, которую продукт может заменить | [AdminApiTokenController.java](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/AdminApiTokenController.java), [AdminTokenViewController.java:29](../../openapi-tokens-sample-ui/src/main/java/ru/openapi/tokens/sample/ui/AdminTokenViewController.java#L29) (там защита есть) | класс-level `@PreAuthorize("hasRole('ADMIN')")` (безопасно: без `@EnableMethodSecurity` аннотация игнорируется) + свойство для отключения |
| M-6 | Админские выборки без тенант-скоупа: `listAll()`/`getAny()` отдают токены всех тенантов | [DefaultApiTokenService.java:140-149](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/service/DefaultApiTokenService.java#L140-L149) | фильтр по тенанту + `Pageable` |
| M-7 | Аудит не обрезает `endpoint`/`user_agent`/`ip` под длину колонок → INSERT падает, событие теряется молча | [JpaAuditLogRepository.java:28-43](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/JpaAuditLogRepository.java#L28-L43), `V1:47,51,52` | обрезать/валидировать до вставки + счётчик потерь |
| M-8 | `AsyncAuditRecorder` противоречит своему javadoc: `executor.execute(...)` **вне** try, при отказе `TaskRejectedException` улетит в фильтр; очередь неограничена, нет graceful shutdown | [AsyncAuditRecorder.java:28-38](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/audit/AsyncAuditRecorder.java#L28-L38), [OpenApiTokensAutoConfiguration.java:127-134](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensAutoConfiguration.java#L127-L134) | обернуть `execute` в try/catch; `setQueueCapacity`, `CallerRunsPolicy`, `waitForTasksToCompleteOnShutdown(true)` |
| M-9 | Аудит успеха пишется **до** обработчика с зашитым 200; при 429 теряется `tokenId`, хотя он есть в исключении | [ApiTokenAuthenticationFilter.java:63-93](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/ApiTokenAuthenticationFilter.java#L63-L93), [RateLimitExceededException.java:10-19](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/RateLimitExceededException.java#L10-L19) | писать после цепочки с `response.getStatus()`; в 429-ветке передавать `ex.getTokenId()` |
| M-10 | Аудит-пагинация `offset/limit` молча игнорирует неровный offset (`offset=15, limit=10` → строки 10–19; `limit<=0` → 20) | [JpaAuditLogRepository.java:46-52](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/JpaAuditLogRepository.java#L46-L52) | явные `LIMIT/OFFSET` в запросе или `Pageable` в порту |
| M-11 | Уникальность `scope_mapping` не работает при `tenant_id IS NULL` (NULL-ы в PostgreSQL различны); `save` всегда генерирует новый UUID → логические дубли | [V1__api_tokens.sql:38](../../openapi-tokens-persistence-jpa/src/main/resources/db/migration/V1__api_tokens.sql#L38), [JpaScopeMappingRepository.java:64](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/JpaScopeMappingRepository.java#L64) | `UNIQUE NULLS NOT DISTINCT` (PG 15+) или частичные индексы + upsert по натуральному ключу |
| M-12 | `findAll()`/`findById()` без entity graph → N+1 в админском списке (`peek(e -> e.getScopes().size())`) | [JpaApiTokenRepository.java:37-61](../../openapi-tokens-persistence-jpa/src/main/java/ru/openapi/tokens/persistence/internal/JpaApiTokenRepository.java#L37-L61) | `@EntityGraph(attributePaths="scopes")` на переопределённых `findAll()`/`findById()` |
| M-13 | Неизвестные JSON-поля игнорируются: опечатка `expireAt` вместо `expiresAt` → создаётся **бессрочный** токен | Jackson default в Boot | `@JsonIgnoreProperties(ignoreUnknown = false)` на DTO |
| M-14 | `expiresAt` в прошлом принимается → токен мёртв при рождении, но занимает квоту | `CreateTokenRequest` | `@Future` + ограничение горизонта |
| M-15 | Ответ `POST /tokens` с сырым секретом без `Cache-Control: no-store` | [UserApiTokenController.java:43-50](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/UserApiTokenController.java#L43-L50) | `cacheControl(CacheControl.noStore())` |
| M-16 | `DELETE /tokens/{id}` не идемпотентен: повтор → 400 | [ApiToken.java:54-63](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/ApiToken.java#L54-L63) | 204, если уже `REVOKED` |
| M-17 | DTO не отдаёт `slidingTtlSeconds`, параметры лимита, `createdBy` → клиент не может прочитать то, что задал | [ApiTokenWebMapper.java:34-49](../../openapi-tokens-web/src/main/java/ru/openapi/tokens/web/ApiTokenWebMapper.java#L34-L49) | расширить `ApiTokenResponse` |
| M-18 | `scopes.mode` — `String`: опечатка (`mappd`) молча трактуется как `identity`, т.е. «сырые authority» | [OpenApiTokensAutoConfiguration.java:90](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensAutoConfiguration.java#L90), [OpenApiTokensProperties.java:66-75](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensProperties.java#L66-L75) | заменить на enum (`ScopeMode`) — тогда опечатка падает при биндинге |
| M-19 | Автоконфигурации без `after`: `PersistenceAutoConfiguration` полагается на `@ConditionalOnBean(DataSource.class)`, порядок держится на алфавите имён классов | [OpenApiTokensPersistenceAutoConfiguration.java:12-19](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensPersistenceAutoConfiguration.java#L12-L19) | `@AutoConfiguration(after = DataSourceAutoConfiguration.class)` + `after = OpenApiTokensPersistenceAutoConfiguration.class` |
| M-20 | `ArchitectureTest` проходит «вхолостую»: `allowEmptyShould(true)`, а пакетов `..dto..` нет на classpath модуля персистентности | [ArchitectureTest.java:18-28](../../openapi-tokens-autoconfigure/src/test/java/ru/openapi/tokens/autoconfigure/ArchitectureTest.java) | перенести правило в модуль, где виден `web`, убрать `allowEmptyShould` |
| M-21 | Нет тестов фильтра/провайдера/цепочки безопасности и обработчика ошибок (самый горячий путь) | `docs/operations/testing.md` §4 | покрыть `ApiTokenAuthenticationFilter`, `ApiTokenAuthenticationProvider`, `ApiTokenPermissionEvaluator`, `InternalTokenOwnerResolver`, `ApiTokenExceptionHandler`, HTTP-тесты 401/403/429 |
| M-22 | Нет JaCoCo/форматтера/`-Werror`: заявление README про «Coverage baseline» непроверяемо, javadoc-ошибки не валят сборку | [build.gradle.kts:38-48](../../build.gradle.kts#L38-L48) | JaCoCo + порог, Spotless/Checkstyle, `-Xlint:all -Werror`, `isFailOnError=true` |
| M-23 | Gradle 9: `useJUnitPlatform()` без `junit-platform-launcher` на runtime classpath (подтверждено отчётом problems) | [build.gradle.kts:54-56](../../build.gradle.kts#L54-L56) | `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` |
| M-24 | `token.default-ttl` (90 дней) не читается: токен без `expiresAt` становится **бессрочным**, хотя документация обещает default TTL | [DefaultApiTokenService.java:83](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/service/DefaultApiTokenService.java#L83), [OpenApiTokensProperties.java:228](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensProperties.java#L228) | применять `defaultTtl` при `expiresAt == null`; остальные мёртвые свойства реализовать или удалить |
| M-25 | `ApiTokenPermissionEvaluator` регистрируется только как `ApiTokenPermissionChecker`, а `PermissionEvaluator`-бина нет → `hasPermission(...)` в `@PreAuthorize` всегда false; сам `hasPermission` игнорирует переданный `Authentication` и читает thread-local | [OpenApiTokensAutoConfiguration.java:180-182](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensAutoConfiguration.java#L180-L182), [ApiTokenPermissionEvaluator.java:26-45](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/ApiTokenPermissionEvaluator.java#L26-L45) | регистрировать как `PermissionEvaluator` (+чекер) и использовать переданную аутентификацию |
| M-26 | `InternalTokenOwnerResolver` принимает анонимную аутентификацию: `AnonymousAuthenticationToken.isAuthenticated() == true` → владелец `anonymousUser` | [InternalTokenOwnerResolver.java:17-32](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/InternalTokenOwnerResolver.java#L17-L32) | явно отклонять `AnonymousAuthenticationToken` |
| M-27 | Смена `hashing.algorithm` молча обнуляет все токены: хеш хранится без маркера алгоритма, verify возвращает `false`, пути перехеширования нет | [TokenCredentials.java](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/TokenCredentials.java), [TokenHasherFactory.java](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/hash/TokenHasherFactory.java) | хранить `{argon2id}`/`{bcrypt}`-префикс, выбирать hasher по маркеру, rehash при успешной проверке |
| M-28 | `@ConditionalOnBean(HttpSecurity.class)` делает цепочку токенов необязательной: условие проходит только благодаря порядку автоконфигураций (проверено runtime-пробой, сейчас работает) | [OpenApiTokensAutoConfiguration.java:246](../../openapi-tokens-autoconfigure/src/main/java/ru/openapi/tokens/autoconfigure/OpenApiTokensAutoConfiguration.java#L246) | `@AutoConfiguration(after = SecurityAutoConfiguration.class)`; иначе `ObjectProvider<HttpSecurity>`/`@ConditionalOnClass` |
| M-29 | `touchLastUsed` бросает `InvalidTokenStateException`, если статус изменился между проверкой и записью → uncaught в фильтре → 500 вместо 401 (связано с H-1) | [DefaultApiTokenService.java:164-172](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/service/DefaultApiTokenService.java#L164-L172) | точечный UPDATE, который не падает при смене статуса (H-1), либо маппинг в 401 |
| M-30 | Токен с пустым набором authority считается успешно аутентифицированным (`.anyRequest().authenticated()` его пропускает) | [DefaultApiTokenAuthenticator.java:70-72](../../openapi-tokens-security/src/main/java/ru/openapi/tokens/security/DefaultApiTokenAuthenticator.java#L70-L72) | трактовать пустой набор как отказ (fail-closed) либо явный флаг конфигурации |
| M-31 | `TokenExpiry.isExpired` возвращает `false` (fail-open), если `slidingTtl != null`, а `lastUsedAt` и `createdAt` — `null` | [TokenExpiry.java:64-70](../../openapi-tokens-core/src/main/java/ru/openapi/tokens/token/TokenExpiry.java#L64-L70) | fail-closed при отсутствии якоря |
| M-32 | Нет индекса под `findByTokenScopes` (`scope_mapping(token_scope, tenant_id)`) → последовательное сканирование на каждом маппинге | `V1__api_tokens.sql:35-39` | добавить индекс в миграции |

## 6. Низкий приоритет / гигиена

| # | Проблема | Решение |
|---|---|---|
| L-1 | Дублирующий индекс `idx_api_token_prefix` (есть `uq_api_token_prefix`) и неиспользуемый `idx_api_token_tenant_id` | миграция `DROP INDEX` |
| L-2 | Комментарий в `JpaApiTokenRepository#save` про «reload with graph after flush» не соответствует коду | удалить/реализовать |
| L-3 | `deleteById` = SELECT + DELETE | `@Modifying @Query("delete ...")` |
| L-4 | `TokenExpiry.slidingTtlSeconds()` → `Math.toIntExact` (переполнение при TTL > ~68 лет) | ограничить `slidingTtl` |
| L-5 | Модуль `openapi-tokens-web` объявляет зависимость на `openapi-tokens-security`, но не использует её | убрать |
| L-6 | `openapi-tokens-web` объявляет 4 cucumber-зависимости при полном отсутствии feature-файлов в модуле | убрать до появления тестов |
| L-7 | `WebTestApplication` не используется ни одним тестом | удалить или подключить через `@ContextConfiguration` |
| L-8 | Нет springdoc/OpenAPI-аннотаций и `/v3/api-docs`, `audit()` возвращает `List<?>` | springdoc (опционально) + `AuditEventResponse` |
| L-9 | Нет `maven-publish`, при этом README предлагает координаты `ru.openapi:...:0.1.0-SNAPSHOT` | добавить публикацию либо переписать README |
| L-10 | `withJavadocJar`/`withSourcesJar` применяются и к сэмплам | ограничить библиотечными модулями |
| L-11 | Дублирующиеся декларации зависимостей (`sample-ui` как `implementation` и `testImplementation`; junit/assertj/mockito + `spring-boot-starter-test` в каждом модуле) | почистить |
| L-12 | Дев-креды в репозитории (`openapi/openapi`, `admin/admin`, `ui-secret`) | оставить как demo, но пометить в docs |
| L-13 | `mkdocs.yml`: `content.action.edit` включён, а `repo_url`/`edit_uri` закомментированы | раскомментировать или выключить |
| L-14 | `amplicode.xml` в корне при игнорируемом `.idea/` | убрать из репозитория |
| L-15 | Нет `distributionSha256Sum` у wrapper, нет `.editorconfig`/version catalog | добавить |
| L-16 | `AsyncAuditRecorder` и `RateLimiter` не покрыты метриками (Micrometer) | счётчики отказов/лимитов |
| L-17 | Ключ ведра лимитера включает параметры политики (`tokenId:requests:window`): изменение политики молча сбрасывает счётчик и «утекает» старое ведро | ключ только по `tokenId` + eviction/очистка при отзыве |
| L-18 | `sendError` не отдаёт `WWW-Authenticate: Bearer` (401) и `Retry-After` (429); цепочка `/api/openapi/**` дополнительно шлёт Basic-challenge | свой `AuthenticationEntryPoint` + `AccessDeniedHandler` (см. Этап 4) |
| L-19 | `prefix-length` допускает 4 символа (16-битное пространство) → коллизии при вставке вместо генерации нового префикса | минимум 8, ретрай на коллизию |

## 7. Что подтверждено как корректное (не трогать)

- **Разделение модулей и направление зависимостей**: core без Spring, порты/адаптеры, `package-info` в каждом модуле.
- **Секреты**: 32 байта `SecureRandom`, хранится только хеш (Argon2id по умолчанию, 3 итерации / 64 МиБ),
  сырой токен не персистится, в API отдаётся только `prefix`; `token_hash` не утекает в DTO.
- **Поиск по префиксу**: `uq_api_token_prefix` + `findByPrefix` с `@EntityGraph` — O(1) и без N+1.
- **Авторизация владельца**: `requireOwnedToken` отдаёт **404** чужого токена (нет утечки существования).
- **Цепочки безопасности**: runtime-проба подтвердила `demo → 403`, `admin → 200`, аноним → `401`,
  UI → 302 на `/login`; CSRF отключён только на stateless API; UI-цепочка защищает `/admin/**`.
- **Длина колонок** совпадает с JPA-аннотациями (`prefix` 16 = `MAX_PREFIX_LENGTH`, audit 512/64),
  `api_token_scope` с PK и `ON DELETE CASCADE` — корректно.
- **Транзакции**: `@Transactional(readOnly=true)` на классе декоратора + переопределения на запись работают
  через JDK-прокси (подтверждено зелёными интеграционными тестами).
- **Сравнение хешей — constant-time** в обоих хешерах (favre `Bytes.equalsConstantTime`, argon2 — сравнение
  в нативной `argon2_verify`); сырой секрет не удерживается в аутентификации (`ApiTokenAuthentication.authenticated(..., null)`).
- **`Bucket4jRateLimiter` потокобезопасен** (`ConcurrentHashMap` + thread-safe `Bucket`); проблема только в росте памяти.
- **Изоляция `ProviderManager`**: локальный менеджер не подменяет глобальный `DaoAuthenticationProvider`
  и не ломает form-login/httpBasic — комментарий в автоконфигурации соответствует коду.
- **CSRF** отключён только на stateless-цепочках (`/api/openapi/**`, `/api/demo/**`), UI-цепочка с сессией
  сохраняет `CsrfFilter`.
- **Документация**: `mkdocs build --strict` проходит, `nav` совпадает с `docs/`, внутренние ссылки/якоря
  разрешаются; roadmap честно фиксирует нереализованные свойства.

## 8. Валидация скоупов — целевой дизайн

### 8.1. Требования

1. Закрыть C-1 по умолчанию (fail-closed), не ломая штатный сценарий «пользователь делегирует
   подмножество объявленных скоупов».
2. Точка проверки — одна: `DefaultApiTokenService.create` (скоупы неизменяемы после создания).
3. Плучаемость: продукт может подменить политику бином (как `TokenHasher`, `ScopeResolver`, `RateLimiter`).
4. Диагностика: 4xx с перечнем отклонённых скоупов и причиной, без секретов.
5. Никаких изменений схемы БД в базовом варианте (расширение каталога — отдельная фаза).

### 8.2. SPI (модуль `openapi-tokens-core`)

```java
package ru.openapi.tokens.token.spi;

/** Валидация запрошенных скоупов перед выпуском токена. */
@FunctionalInterface
public interface ScopeValidator {
    void validate(ScopeValidationContext context) throws InvalidScopeException;
}
```

```java
package ru.openapi.tokens.token;

/** Контекст валидации: кто, для какого тенанта и что просит. */
public record ScopeValidationContext(
        String ownerId,
        String tenantId,
        Set<String> requestedScopes,
        Set<String> callerAuthorities,   // пусто, если права вызывающего недоступны
        boolean privileged               // вызывающий входит в privileged-authorities
) { }
```

```java
package ru.openapi.tokens.token.exception;

public class InvalidScopeException extends RuntimeException {
    private final Set<String> invalidScopes;   // для ProblemDetail.properties
    private final String reason;               // UNKNOWN_SCOPE | DENIED_PATTERN | NOT_GRANTABLE | ...
}
```

```java
package ru.openapi.tokens.token.spi;

/** Права текущего вызывающего (в core — без Spring). */
public interface CallerAuthorityResolver {
    Set<String> currentAuthorities();
    boolean isPrivileged();
}
```

### 8.3. Реализации (core)

| Класс | Семантика |
|---|---|
| `NoOpScopeValidator` | ничего не проверяет (`validation.mode: off`) |
| `DenyPatternScopeValidator` | отклоняет скоупы по regex-списку; **всегда** применяется, кроме `off` |
| `CatalogScopeValidator` | скоуп обязан присутствовать в `ScopeCatalog.listScopes()` |
| `CallerAuthorityScopeValidator` | каждый запрошенный скоуп (identity) или каждое разрешённое authority (mapped) ⊆ `callerAuthorities`, если вызывающий не privileged |
| `CompositeScopeValidator` | AND по списку (первая ошибка побеждает) |

Сборка в автоконфигурации:

```java
@Bean
@ConditionalOnMissingBean
ScopeValidator scopeValidator(OpenApiTokensProperties props,
                              ObjectProvider<ScopeCatalog> catalog,
                              ObjectProvider<CallerAuthorityResolver> caller,
                              ScopeResolver scopeResolver) { /* composite по props.scopes.validation */ }
```

`CallerAuthorityResolver` реализуется в `openapi-tokens-security` через `SecurityContextHolder`
(права `Authentication`, `privileged` — по списку authority из конфигурации). В `keycloak`-режиме
это права JWT после маппинга ролей; при управлении токенами другим API-токеном — authority этого токена.

### 8.4. Конфигурация

```yaml
openapi:
  tokens:
    scopes:
      mode: identity
      validation:
        mode: catalog              # off | deny-list | catalog | strict
        deny-patterns:             # применяется при любом mode, кроме off
          - "ROLE_.*"
          - "SCOPE_.*"
        privileged-authorities:    # кому разрешено делегировать что угодно
          - "ROLE_ADMIN"
        require-caller-authority: true   # только для mode=strict
```

| Режим | Что проверяется | Рекомендация |
|---|---|---|
| `off` | ничего | только для обратной совместимости/тестов |
| `deny-list` | `deny-patterns` | минимум для legacy-миграции |
| `catalog` | `deny-patterns` + членство в `ScopeCatalog` | **default** для `identity` и `mapped` |
| `strict` | `catalog` + «не делегируй больше, чем имеешь» | мультитенант, `admin:all`-подобные каталоги |

**Почему дефолт `catalog`, а не `strict`.** В self-service сценарии права пользователя — это роли
(`ROLE_USER`), а скоупы — прикладные (`payments:read`), поэтому `strict` по умолчанию сломает
штатную выдачу. `catalog` закрывает «мусорные» и необъявленные authority (включая `ROLE_ADMIN`,
если его нет в справочнике), а `deny-patterns: ROLE_.*` закрывает повышение привилегий даже при
наличии `ROLE_ADMIN` в каталоге. `strict` рекомендуется включать там, где каталог содержит
«сильные» скоупы вида `admin:all`.

### 8.5. Точка вызова

```java
// DefaultApiTokenService#create
final TokenOwner owner = ownerResolver.resolveCurrentOwner();
final String tenantId = resolveTenant(command, owner);          // см. C-2
scopeValidator.validate(new ScopeValidationContext(              // ← до квоты и генерации секрета
        owner.ownerId(), tenantId, command.scopes(),
        callerAuthorityResolver.currentAuthorities(),
        callerAuthorityResolver.isPrivileged()));
final long existing = repository.countByOwnerId(owner.ownerId());
...
```

HTTP-контракт: `422 Unprocessable Entity` + `ProblemDetail`
(`title=Invalid Scopes`, `properties.invalidScopes=[...]`, `properties.reason=UNKNOWN_SCOPE`).
Альтернатива для совместимости с текущими 400 — оставить 400, но обязательно с `invalidScopes`.

### 8.6. Тесты (обязательны вместе с реализацией)

- unit: каждый валидатор + composite (в т.ч. «deny-list побеждает catalog»);
- `DefaultApiTokenServiceTest`: секрет/токен **не создаются** при `InvalidScopeException`, квота не расходуется;
- mockmvc: 422 и тело ProblemDetail; скоуп `ROLE_ADMIN` отклонён; валидный скоуп из каталога — 201;
- cucumber-фича `features/scopes/scope-validation.feature` (BR-7…BR-9);
- `OpenApiTokensAutoConfigurationTest`: дефолтный `ScopeValidator` есть, `@ConditionalOnMissingBean` работает;
- регресс: `ScopesResolutionSteps`, `SampleApplicationIT`, Keycloak-тесты остаются зелёными.

### 8.7. Документация

- `docs/features/scopes.md`: убрать admonition «Скоупы из запроса не проверяются», описать режимы;
- `docs/features/security.md`: убрать раздел «Повышение привилегий через скоупы» (или переписать в «закрыто»);
- `docs/configuration.md`: новые свойства; `docs/api/rest-api.md`: 422 и `invalidScopes`;
- `docs/operations/roadmap.md`: 1.1 → закрыто, обновить «Предлагаемый план работ».

## 9. Что дополнить (функциональность)

| Приоритет | Дополнение | Зачем |
|---|---|---|
| 1 | **Освобождение квоты/удаление токена** (`DELETE` с аудитом или счёт только `ACTIVE`/`BLOCKED`) | иначе пользователь выжигает квоту отозванными токенами |
| 1 | **Эффективный статус** (`EXPIRED` вычисляемо или планировщик) | сейчас `EXPIRED` недостижим |
| 1 | **Аудит жизненного цикла**: `CREATE`/`REVOKE`/`BLOCK` | нельзя ответить «кто выдал/отозвал токен» |
| 1 | **Метрики Micrometer**: создание, отказы аутентификации, 429, потери аудита | эксплуатация и алертинг |
| 2 | **Единый JSON для 401/429 + `Retry-After`** (свой `AuthenticationEntryPoint`) | сейчас HTML-страница контейнера |
| 2 | **Пагинация/фильтры** в админском API и в `AuditLogRepository` | сейчас `findAll()` и фиксированные limit |
| 2 | **Redis `RateLimiter`** (порт готов) + TTL-эвикция ведёр | в кластере лимит ×N узлов |
| 2 | **`Cache-Control: no-store`**, идемпотентность (`Idempotency-Key`), ETag/`If-Match` | корректные ретраи и кеши |
| 3 | **springdoc + `AuditEventResponse`**, реальный OpenAPI вместо ручного `rest-api.md` | контракт для клиентов |
| 3 | **Ротация/партиционирование `api_token_audit_log`** | неограниченный рост |
| 3 | **Маскирование PII** (`ip`, `user_agent`) и retention | security-ревью |
| 3 | **`maven-publish` + version catalog + dependency locking** | публикация и воспроизводимость |
| 4 | **Реактивная поддержка** (WebFlux) | сейчас starter тянет `starter-web` жёстко |

## 10. Исправления в существующей документации

| Где | Что не так | Как исправить |
|---|---|---|
| `docs/operations/testing.md:73` | «158 тестов» — фактически **160** | обновить число/таблицу |
| `docs/operations/testing.md:84` | sample-ui «31», в исходниках **33** `@Test` | обновить |
| `docs/operations/roadmap.md:126,150` (3.15) | `PUBLIC_PATHS` «не входят в securityMatchers» — уже входят ([TokenUiSecurity.java:40-42](../../openapi-tokens-sample-ui/src/main/java/ru/openapi/tokens/sample/ui/TokenUiSecurity.java#L40-L42)), покрыто `UiStaticResourcesTest` | удалить пункт |
| `docs/architecture/index.md:55-57` | «направление зависимостей проверяется ArchUnit» — тест вхолостую (M-20) | починить тест или убрать утверждение |
| `README.md:197` | «Coverage baseline» — JaCoCo нет | подключить JaCoCo или переписать |
| `README.md:27-29` | координаты артефакта, которого нет в репозиториях | `maven-publish` или пометка «собирается локально» |
| `README.md:226`, `templates/ui/scopes.html:13` | `/ui/scopes` «берёт данные из `GET /api/openapi/scopes`» — фактически бин `ScopeCatalog` | поправить текст |
| `README.md:91`, `docs/features/index.md:27`, `docs/architecture/index.md:118` | фильтр «для `/api/openapi/**`» — он ещё и глобальный сервлет-фильтр (M-1) | описать фактическое поведение или отключить автрегистрацию |
| `docs/features/rate-limiting.md:73-74` | 429: `tokenId` записывается; `RateLimitExceededException extends AuthenticationException` — неверно | привести в соответствие (M-9) |
| `docs/architecture/data-model.md` | не отражены: отсутствие `@Version`, дубли `scope_mapping` при NULL-тенанте, отсутствие индекса под `findByTokenScopes` | дополнить |

## 11. План работ

### Этап 1 — безопасность и корректность (must have)

- [x] **T1.** SPI `ScopeValidator`/`ScopeValidationContext`/`CallerAuthorityResolver`/`InvalidScopeException` (core).
- [x] **T2.** Реализации `NoOp`/`DenyPattern`/`Catalog`/`CallerAuthority`/`Composite` + unit-тесты.
- [x] **T3.** Свойства `openapi.tokens.scopes.validation.*`, default `catalog`, `deny-patterns: ROLE_.*`; `scopes.mode` → enum.
- [x] **T4.** Вызов валидатора в `DefaultApiTokenService.create` **до** квоты и генерации секрета; тесты «токен не создан».
- [x] **T5.** Обработка `InvalidScopeException` → 422/400 `ProblemDetail` c `invalidScopes`; сузить `@RestControllerAdvice` (M-2, M-3).
- [x] **T6.** Тенант из запроса: `allow-tenant-override` (default `false`), тесты (C-2).
- [x] **T7.** Валидация DTO: `@Positive`/`@Max` на лимиты, `@Size(max=128)` на `tenantId` и скоупы, `@Future` на `expiresAt`, «оба или ни одного» (H-5, H-6, M-14).
- [x] **T8.** `@PreAuthorize` на админском контроллере (defense in depth) (M-5).
- [x] **T9.** Тесты фильтра/провайдера/цепочки/обработчика ошибок (M-21).
- [x] **T10.** Обновить `scopes.md`, `security.md`, `configuration.md`, `rest-api.md`, `roadmap.md`.
- [x] **T11.** Пред-аутентификационный лимитер по `prefix`+IP до проверки хеша, лимит на неуспешные попытки (H-8).
- [x] **T12.** Constant-work путь на отказ + единый ответ; `prefix-length` минимум 8 и ретрай на коллизию (H-9, L-19).
- [x] **T13.** Применять `token.default-ttl` при отсутствии `expiresAt`; решить судьбу остальных мёртвых свойств (M-24).
- [x] **T14.** Анонимная аутентификация как владелец → отказ; пустой набор authority → отказ (M-26, M-30).
- [x] **T15.** Маркер алгоритма в хеше + rehash при успешной проверке (M-27).
- [x] **T16.** `PermissionEvaluator` как бин + использование переданного `Authentication` (M-25).
- [x] **T17.** `@AutoConfiguration(after = ...)` для security/persistence (M-19, M-28).
- [x] **T18.** `FilterRegistrationBean#setEnabled(false)` для фильтра токенов (M-1).

### Этап 2 — персистентность и эксплуатация

- [x] **T19.** `@Version` + миграция `V3`; точечный UPDATE для `touchLastUsed`; pessimistic lock на смену статуса (H-1, M-29).
- [x] **T20.** `Persistable`/`persist` для создания (H-2).
- [x] **T21.** Атомарная квота (H-3).
- [x] **T22.** Батчевый `ScopeMappingSource` + один запрос (H-4); `@EntityGraph` на `findAll`/`findById` (M-12); индекс под `findByTokenScopes` (M-32).
- [x] **T23.** Аудит: обрезка полей, `tokenId` при 429, статус после обработчика, ограниченная очередь + graceful shutdown, метрики (M-7…M-9, L-16).
- [x] **T24.** Миграция `V3`: `UNIQUE NULLS NOT DISTINCT`, drop дублирующего индекса (M-11, L-1).
- [x] **T25.** Пагинация админского API и аудита (M-6, M-10).
- [x] **T26.** Идемпотентность `DELETE`, `Cache-Control: no-store`, расширение DTO (M-15…M-17).
- [x] **T27.** Eviction ведёр лимитера, ключ только по `tokenId` (L-17); fail-closed в `TokenExpiry.isExpired` при отсутствии якоря (M-31).

### Этап 3 — CI и качество

- [x] **T28.** CI: job `test` (Gradle + Docker), `SITE_URL` для GitLab Pages (H-7).
- [x] **T29.** JaCoCo + пороги, `-Xlint:all -Werror`, `javadoc isFailOnError=true`, `junit-platform-launcher` (M-22, M-23). Spotless/Checkstyle не подключены.
- [x] **T30.** `ArchitectureTest` перенесён в `openapi-tokens-autoconfigure` (4 правила, без `allowEmptyShould`); убраны неиспользуемые зависимости и `WebTestApplication` (M-20, L-5…L-7).
- [x] **T31.** `maven-publish` для библиотечных модулей + `distributionSha256Sum` + `.editorconfig`. Version catalog и dependency locking не сделаны.
- [x] **T32.** Документация синхронизирована (раздел 10).

### Этап 4 — развитие

- [x] **T33.** Эффективный статус `EXPIRED`: `ApiToken.effectiveStatus(Instant)` + поле `effectiveStatus` в DTO. Планировщик не нужен.
- [x] **T34.** Квота считается по ACTIVE+BLOCKED, отзыв освобождает слот. Hard-delete не добавлен (не требуется).
- [x] **T35.** События `CREATE_TOKEN`/`REVOKE_TOKEN`/`BLOCK_TOKEN`/`UNBLOCK_TOKEN`.
- [x] **T36.** `application/problem+json` для 401/429 + `WWW-Authenticate`/`Retry-After` (L-18).
- [ ] **T37.** ❌ Redis `RateLimiter`, springdoc, партиционирование аудита, реактивная поддержка, Micrometer-метрики — остаются на будущее.

## 12. Критерии приёмки

1. `POST /api/openapi/tokens` со скоупом `ROLE_ADMIN` (и с любым необъявленным скоупом при
   `validation.mode ∈ {catalog, strict}`) → 4xx, токен **не** создан, аудит/квота не затронуты.
2. Скоуп из каталога в штатном сценарии (`payments:read` для `demo`) → 201 (обратная совместимость).
3. `tenantId` из запроса не позволяет выйти за пределы тенанта владельца при `allow-tenant-override=false`.
4. `rateLimitRequests=0`/`windowSeconds=0`/`tenantId` длиной 129/скоуп длиной 200 → 4xx, а не сохранённый
   «сломанный» токен и не 500.
5. Параллельные `revoke` и обращение с токеном не воскрешают токен (тест на гонку);
   параллельные `create` не превышают квоту.
6. Все существующие 160 тестов зелёные + новые; добавлен CI-job, который реально их запускает.
7. `mkdocs build --strict` зелёный; документация не содержит утверждений, противоречащих коду
   (раздел 10 закрыт).

## Связанные разделы

- [Ограничения и roadmap](../operations/roadmap.md) — исходный список известных ограничений.
- [Скоупы и права](../features/scopes.md), [Аутентификация](../features/security.md).
- [Модель данных](../architecture/data-model.md), [Тестирование](../operations/testing.md).
