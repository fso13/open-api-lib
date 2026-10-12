# Ограничения и roadmap

Раздел фиксирует всё, что библиотека **не** делает или делает не так, как ожидается,
с оценкой влияния и способом обхода. Это входные данные для планирования доработок.

!!! success "Security hardening выполнен (2026-10)"
    Большая часть дефектов из первой редакции раздела **закрыта**: валидация скоупов,
    учёт квоты, доверие к тенанту из запроса, пагинация админского API, оптимистичная
    блокировка, JSON-ответы 401/429 и тесты горячего пути безопасности. Ниже сначала идёт
    список исправленного (раздел 1), затем актуальный список того, что ещё не сделано
    (раздел 2), и план работ по этапам (раздел 4).

---

## 1. Что уже исправлено (2026-10)

### 1.1. Критичные и существенные дефекты

| # | Было | Что сделано |
|---|---|---|
| 1.1 | Повышение привилегий через произвольные скоупы: `POST /api/openapi/tokens` принимал любой непустой скоуп, включая `ROLE_ADMIN` | Валидация скоупов при создании: `openapi.tokens.scopes.validation.mode` (default `catalog`), deny-pattern `ROLE_.*`, отказ → `422 Unprocessable Entity` с `ProblemDetail` (`reason`, `invalidScopes`). Default-режим `catalog` требует, чтобы скоуп был объявлен в справочнике |
| 2.2 | Квота не освобождалась при отзыве: `countByOwnerId` считал все токены | `countUsableByOwnerId` считает только `ACTIVE` + `BLOCKED`; отозванные и истёкшие квоту не занимают |
| 2.3 | Мёртвые свойства `audit.enabled`, `audit.async`, `audit.record-failures`, `token.default-ttl` | Все четыре читаются кодом: аудит можно выключить/сделать синхронным, неуспешные попытки — не писать, TTL по умолчанию применяется при отсутствии `expiresAt`. **Мёртвым осталось только `rate-limit.backend`** (см. 2.1) |
| 2.4 | `tenantId` из запроса записывался в токен без проверки | `openapi.tokens.scopes.allow-tenant-override` (default `false`): чужой тенант → `403 Forbidden` (`TenantNotAllowedException`), тенант берётся из security context |
| 2.5 | Фильтр аутентификации и цепочка безопасности не были покрыты тестами | Добавлены `ApiTokenAuthenticationFilterTest` (401/429, пропуск, `SecurityContext`, аудит), `SecurityContextCallerAuthorityResolverTest`, тесты валидаторов `ScopeValidatorTest` и e2e `TokenSecurityIT`. Всего в проекте 229 тестов |
| 2.6 | Админский API отдавал все токены без пагинации | `GET /api/openapi/admin/tokens?offset=&limit=` + заголовок `X-Total-Count`; лимит ≤ 500. Аудит (`GET /{id}/audit`, `GET /api/openapi/tokens/{id}/audit`) тоже принимает `offset`/`limit` |

Режимы валидации скоупов (`openapi.tokens.scopes.validation.mode`):

```yaml
openapi:
  tokens:
    scopes:
      validation:
        mode: catalog            # off | deny-list | catalog | strict
        deny-patterns:
          - "ROLE_.*"            # нельзя выпустить токен с ролью Spring Security
        privileged-authorities:
          - ROLE_ADMIN           # учитывается только в режиме strict
```

- `off` — валидации нет (совместимость/тесты);
- `deny-list` — отклоняются только скоупы, попавшие под `deny-patterns`;
- `catalog` (default) — `deny-list` + скоуп обязан быть известен справочнику;
- `strict` — `catalog` + «нельзя делегировать больше, чем есть у вызывающего».

E2e-проверка — `openapi-tokens-sample/src/test/java/ru/openapi/tokens/sample/TokenSecurityIT.java`
(отказ по `ROLE_*`, отказ по неизвестному скоупу, чужой тенант → 403, 401 как `problem+json`).
Юнит-проверка валидаторов — `ScopeValidatorTest` в `openapi-tokens-core`.

### 1.2. Умеренные проблемы

| # | Было | Что сделано |
|---|---|---|
| 3.1 | Вёдра rate limiter не удалялись | LRU-эвикция (по умолчанию 10 000 ведёр), ключ — только `tokenId`. **Redis-реализации по-прежнему нет** |
| 3.2 | Аудит писал только события аутентификации | Добавлены события `CREATE_TOKEN`, `REVOKE_TOKEN`, `BLOCK_TOKEN`, `UNBLOCK_TOKEN` |
| 3.4 | `DELETE` не идемпотентен (повтор → 400) | Повторный `DELETE /api/openapi/tokens/{id}` теперь отвечает `204 No Content` |
| 3.5 | 401/429 отдавались без JSON и заголовков | Ответы рендерятся вручную как `application/problem+json`; 401 → `WWW-Authenticate: Bearer`, 429 → `Retry-After` (`rate-limit.retry-after-seconds`) |
| 3.6 | `touchLastUsed` перезаписывал всю строку на каждый запрос | Точечный `UPDATE … SET last_used_at = ? WHERE id = ? AND status = ACTIVE` (`@Modifying`); запись осталась, но объём и риск перезаписи статуса убраны |
| 3.7 | Пагинация аудита считалась `offset / limit` и игнорировала некруглые offset | Использован настоящий `offset` (`setFirstResult`), `limit` ограничен сверху (≤ 500; в адаптере — защитный `MIN(limit, 500)`) |
| 3.8 | `idx_api_token_prefix` дублировал индекс `uq_api_token_prefix` | Удалён миграцией `V3__hardening.sql` (вместе с `idx_api_token_tenant_id`); добавлен полезный `idx_scope_mapping_token_scope_tenant` |
| 3.9 | `scope_mapping` допускал логические дубли при `tenant_id IS NULL` | `V3__hardening.sql`: `UNIQUE NULLS NOT DISTINCT (token_scope, project_scope, tenant_id)` + дедупликация существующих строк |
| 3.10 | Не было `@Version`/оптимистичной блокировки | Добавлена `api_token.version BIGINT NOT NULL DEFAULT 0` (`V3`); конфликт → `409 Conflict` (`ConcurrentTokenModificationException`) |
| 3.11 | Длинные `endpoint`/`user_agent` (> 512) ломали вставку аудита | `JpaAuditLogRepository` обрезает значения до ширины колонки (`endpoint`, `user_agent`, `ip`, `action`, …) |
| 3.13 | `ApiTokenResponse` не содержал `slidingTtlSeconds` и параметров лимита | В DTO добавлены `slidingTtlSeconds`, `rateLimitRequests`, `rateLimitWindowSeconds`, `createdBy`, `effectiveStatus` |
| 3.15 | Утверждалось, что `PUBLIC_PATHS` не входят в `securityMatchers` | Утверждение **неверно**: `TokenUiSecurity.uiChain` включает `PUBLIC_PATHS` (`/css/**`, `/error`) в `securityMatchers`. Пункт снят |

Дополнительно устранён N+1 при выборке токенов: методы `findAll()`, `findByOwnerId()`,
`findById()`, `findByPrefix()` используют `@EntityGraph(attributePaths = "scopes")`, а
`findPage()` делает два запроса вместо N+1.

### 1.3. Несоответствия документации и кода

| Место | Что было | Что сделано |
|---|---|---|
| `README.md` | Список тестов неполон; «Coverage baseline» не проверяем | Количество тестов и покрытие приведены к факту (229 тестов; core 84.2 %, security 76.1 %, persistence 90.6 %, autoconfigure 67.4 %, web 50.7 %) |
| `README.md` | JaCoCo не подключён | JaCoCo подключён: отчёты `*/build/reports/jacoco/...`, пороги `jacocoTestCoverageVerification` входят в `check` |
| `ArchitectureTest` | Проходил «вхолостую» из-за `allowEmptyShould(true)` в модуле персистентности | Тест перенесён в `openapi-tokens-autoconfigure` (там на classpath и DTO, и `@Entity`), 4 правила, `allowEmptyShould` не используется |
| `openapi-tokens-web/build.gradle.kts` | Зависимость от `openapi-tokens-security` | Зависимость убрана: `web` зависит только от `core` и Spring Security-библиотеки |
| `openapi-tokens-web/build.gradle.kts` | Неиспользуемые Cucumber-зависимости без feature-файлов | Удалены |
| `WebTestApplication` | Тестовый bootstrap-класс без ссылок из тестов | Удалён |
| `JpaApiTokenRepository#save` | Комментарий про «reload with graph» не соответствовал реализации | Комментарий удалён |

---

## 2. Что ещё не сделано

### 2.1. Rate limiting: `rate-limit.backend` и Redis

Свойство `openapi.tokens.rate-limit.backend` **не читается кодом** — это единственное
оставшееся мёртвое свойство. Порт `RateLimiter` готов, но реализации, кроме in-memory
`Bucket4jRateLimiter`, нет.

**Влияние:** в кластере лимит умножается на число узлов; переключить бэкенд конфигурацией
нельзя.

**Решение:** реализовать Redis-бэкенд и начать читать `rate-limit.backend`, либо удалить
свойство и документировать замену бина `RateLimiter`.

### 2.2. Журнал аудита: партиционирование, ротация и retention (бывш. 3.3)

Таблица `api_token_audit_log` растёт неограниченно; ретеншна и партиционирования нет.
Нужны партиции по `created_at` (или регулярная задача очистки) и документированный срок
хранения.

### 2.3. Метрики Micrometer

Нет метрик: создания токенов, отказов аутентификации, ответов 429, потерь аудита.
Без них эксплуатация и алертинг невозможны.

### 2.4. springdoc/OpenAPI и `/v3/api-docs`

REST-контракт описан вручную в `docs/api/rest-api.md`; машинно-читаемой спецификации
и springdoc-эндпоинта нет.

### 2.5. Публикация артефакта в Maven Central/Nexus

`maven-publish` теперь настроен для библиотечных модулей (публикация в локальный или
корпоративный репозиторий возможна), но **репозиторий и подпись не заданы**, поэтому в
публичный репозиторий артефакт пока не опубликован. Нужны `publishing.repositories`,
подпись (`signing`) и релизный пайплайн.

### 2.6. Качество сборки

Не используются: version catalog (`libs.versions.toml`), Spotless/Checkstyle, dependency
locking. Версии зависимостей задаются строками, воспроизводимость сборки не зафиксирована.

### 2.7. Жизненный цикл: фоновый `EXPIRED` (бывш. 2.1) и hard-delete

- Статус `EXPIRED` **не выставляется в БД**: фонового планировщика нет, фактический статус
  вычисляется на лету (`effectiveStatus` в `ApiToken`/DTO). В БД и в прямых запросах токен
  остаётся `ACTIVE`.
- Операции **физического удаления** токенов нет; квота освобождается отзывом
  (`countUsableByOwnerId`), но строки и аудит остаются.

### 2.8. Маскирование PII и security-ревью

`ip` и `user_agent` пишутся в аудит без маскирования; отдельное security-ревью (энтропия
токена, параметры хеширования, PII, retention) не оформлено.

### 2.9. Прочие умеренные ограничения

| # | Проблема | Влияние | Обход |
|---|---|---|---|
| 3.12 | Нет FK у `api_token_audit_log.token_id` | «Осиротевшие» записи аудита | Учитывать в отчётах |
| 3.14 | Argon2id (64 МиБ, 3 итерации) проверяется на каждом запросе | CPU/память на горячем пути | Кеш проверки, свои параметры хешера |
| 3.16 | Демо-UI: нет i18n, тёмной темы, пагинации; `/ui/scopes` без flash | Не готов к продакшену как есть | Свой UI или доработка |

### 2.10. Несоответствие документации: источник данных `/ui/scopes`

`README.md` и шаблон `/ui/scopes` утверждают, что страница берёт данные из
`GET /api/openapi/scopes`; фактически контроллер обращается к бину `ScopeCatalog` напрямую.
Формулировку нужно поправить (страница и REST-эндпоинт используют один и тот же источник —
бин `ScopeCatalog`, но не друг друга).

---

## 3. Ограничения архитектуры (осознанные)

| Ограничение | Причина | Комментарий |
|---|---|---|
| Нет таблицы пользователей/владельцев | Библиотека не управляет пользователями | `owner_id` — строка из security context |
| Нет автоматического удаления токенов | Аудит и история важнее | Удаление — только через БД или доработку |
| Ядро без Spring | Переносимость и тестируемость | Транзакции только в декораторе авто-конфигурации |
| In-memory лимитер | Отсутствие внешних зависимостей в MVP | Redis заявлен как post-MVP; `rate-limit.backend` пока не читается |
| Формат токена фиксирован | Совместимость | Смена формата — только своим аутентификатором |
| Публикация настроена, но не завершена | `maven-publish` есть, репозиторий и подпись не заданы | Подключение через `includeBuild` / локальный репозиторий |

---

## 4. План работ

### Этап 1 — эксплуатационная зрелость (must have)

1. Партиционирование/ротация `api_token_audit_log` и retention (2.2, 3.3).
2. Метрики Micrometer: создания, отказы, 429, потери аудита (2.3).
3. springdoc/OpenAPI и `/v3/api-docs` (2.4).

### Этап 2 — масштабирование

4. Redis/распределённый `RateLimiter` и чтение `rate-limit.backend` (2.1, 3.1).
5. Кеширование результата проверки токена (3.14).

### Этап 3 — релиз и качество сборки

6. Публикация в Maven Central/Nexus: `publishing.repositories` + `signing` (2.5).
7. Version catalog, Spotless/Checkstyle, dependency locking (2.6).
8. Security-ревью и маскирование PII (`ip`, `user_agent`) (2.8).

### Этап 4 — жизненный цикл и удобство

9. Фоновый перевод токенов в `EXPIRED` (или явная опора на `effectiveStatus`) (2.7).
10. Операция hard-delete токена с аудитом (2.7).
11. Демо-UI: i18n, тёмная тема, пагинация, flash для `/ui/scopes` (3.16).
12. Актуализация документации: `/ui/scopes` и источник данных (2.10).
13. Расширение BDD-покрытия на REST/UI-потоки и теги (`@smoke`, `@regression`).

---

## 5. Что можно сделать без изменений в библиотеке

| Задача | Как |
|---|---|
| Своя политика скоупов | Свой бин `ScopeValidator` (SPI), режимы `validation.mode` |
| Распределённый лимит | Свой бин `RateLimiter` или лимит на API-gateway |
| Аудит для SIEM | Свой `AuditRecorder` (например, с отправкой в Kafka/ClickHouse) |
| Хранение не в PostgreSQL | Свои реализации портов репозиториев |
| Уборка журнала | SQL-задача/партиции |
| Метрики | Micrometer-обёртки вокруг `ApiTokenService` и `AuditRecorder` |
| Интернационализация UI | Свой фронтенд |

---

## 6. Как предложить изменение

1. Сверьтесь с правилами `.cursor/rules/java-senior.mdc` (конструкторная инъекция,
   изоляция DTO и сущностей, BDD для бизнес-требований, LAZY-загрузка, `@Transactional`).
2. Для изменения поведения сначала добавьте тест (юнит/BDD), затем реализацию.
3. Изменения схемы БД — только новой Flyway-миграцией (следующий номер после `V3`).
4. Обновляйте документацию вместе с кодом: этот сайт — источник истины
   (см. [Публикация документации](publishing.md#6-обновление-документации)).

## Связанные разделы

- [Пробелы в тестах](testing.md#4-пробелы-в-покрытии) — детальный список.
- [Аутентификация](../features/security.md#повышение-привилегий-через-скоупы) — контекст проблемы 1.1.
- [Жизненный цикл токена](../features/token-lifecycle.md) — статус `EXPIRED` и вычисляемый фактический статус (2.7).
- [Модель данных](../architecture/data-model.md) — замечания к схеме.
- [Бэкенд-разработчику](../guides/backend.md#7-особенности-о-которых-нужно-знать) — обходы на стороне продукта.
