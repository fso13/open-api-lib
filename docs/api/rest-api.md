# REST API

Библиотека предоставляет три группы эндпоинтов. Доступ регулируется `SecurityFilterChain`
(своим в приложении или готовым из статера); дополнительно админский контроллер защищён
`@PreAuthorize("hasRole('ADMIN')")` (defense in depth — аннотация работает, если приложение
включает method security).

| Группа | Базовый путь | Кто имеет доступ (цепочка статера) |
|---|---|---|
| Токены владельца | `/api/openapi/tokens` | любой аутентифицированный |
| Администрирование | `/api/openapi/admin/tokens` | authority `ROLE_ADMIN` (+ `@PreAuthorize` при method security) |
| Справочник скоупов | `/api/openapi/scopes` | любой аутентифицированный |

Формат данных — JSON (`application/json`), кодировка UTF-8, время — ISO-8601 UTC
(например `2026-10-07T11:00:00Z`), идентификаторы — UUID.

Ошибки — RFC 7807 `application/problem+json` (обработчик `ApiTokenExceptionHandler`).
Аутентификация на уровне фильтра отвечает в том же формате (см.
[Ошибки аутентификации](#ошибки-аутентификации-на-уровне-фильтра)).

---

## Токены владельца

### `POST /api/openapi/tokens` — создать токен

Создаёт токен для текущего владельца и **единственный раз** возвращает секрет.

=== "Запрос"

    ```http
    POST /api/openapi/tokens HTTP/1.1
    Authorization: Basic ZGVtbzpkZW1v
    Content-Type: application/json

    {
      "name": "ci-bot",
      "description": "Токен для CI",
      "scopes": ["payments:read"],
      "expiresAt": "2027-01-01T00:00:00Z",
      "slidingTtlSeconds": 3600,
      "rateLimitRequests": 100,
      "rateLimitWindowSeconds": 60,
      "tenantId": "tenant-a"
    }
    ```

=== "Ответ 201"

    ```http
    HTTP/1.1 201 Created
    Location: /api/openapi/tokens/0f9a1b7c-1a2b-4c3d-8e4f-5a6b7c8d9e0f
    Content-Type: application/json
    Cache-Control: no-store

    {
      "token": {
        "id": "0f9a1b7c-1a2b-4c3d-8e4f-5a6b7c8d9e0f",
        "name": "ci-bot",
        "description": "Токен для CI",
        "prefix": "9f3c1a7b",
        "ownerId": "demo",
        "tenantId": "tenant-a",
        "status": "ACTIVE",
        "effectiveStatus": "ACTIVE",
        "scopes": ["payments:read"],
        "expiresAt": "2027-01-01T00:00:00Z",
        "lastUsedAt": null,
        "slidingTtlSeconds": 3600,
        "rateLimitRequests": 100,
        "rateLimitWindowSeconds": 60,
        "createdBy": "demo",
        "createdAt": "2026-10-07T11:00:00Z",
        "revokedAt": null
      },
      "rawToken": "atk_9f3c1a7b_5d8e…c1"
    }
    ```

Поля запроса:

| Поле | Тип | Обяз. | Ограничения | Описание |
|---|---|---|---|---|
| `name` | `string` | ✔ | не пусто, ≤ 128 | Человекочитаемое имя |
| `description` | `string` | ✗ | ≤ 512 | Описание |
| `scopes` | `string[]` | ✔ | не пусто, ≤ 64 элементов, каждый не пустой и ≤ 128 символов; должны быть известны и не попадать под deny-patterns | Скоупы (см. [Скоупы](../features/scopes.md)) |
| `expiresAt` | `string (ISO-8601)` | ✗ | должен быть **в будущем** | Абсолютный срок действия |
| `slidingTtlSeconds` | `integer` | ✗ | > 0, ≤ 31536000 | Скользящее окно жизни |
| `rateLimitRequests` | `integer` | ✗ | > 0, ≤ 1000000; обязателен вместе с `rateLimitWindowSeconds` | Лимит запросов (нужен вместе со следующим) |
| `rateLimitWindowSeconds` | `integer` | ✗ | > 0, ≤ 31536000; обязателен вместе с `rateLimitRequests` | Окно лимита в секундах |
| `tenantId` | `string` | ✗ | ≤ 128; при `scopes.allow-tenant-override=false` должен совпадать с тенантом владельца | Тенант; если не задан — берётся из владельца |

!!! danger "Неизвестные поля JSON отклоняются"
    Тело запроса разбирается строго: любое неизвестное свойство (например опечатка
    `expireAt` вместо `expiresAt`) приводит к **400**. Раньше Jackson молча игнорировал такие
    поля, и токен выпускался без срока действия.

    Также **400** возвращается, если `rateLimitRequests` задан без `rateLimitWindowSeconds`
    (и наоборот) — неполный лимит означал бы «без ограничений».

Поля ответа: см. [`ApiTokenResponse`](#схемы-данных) и `rawToken`.

!!! warning "Секрет показывается один раз"
    `rawToken` возвращается только этим ответом. В БД хранится хеш, восстановить токен нельзя.
    Ответ помечен `Cache-Control: no-store` — не кешируйте и не логируйте его.

!!! info "Скоупы валидируются"
    Режим задаётся свойством `openapi.tokens.scopes.validation.mode` (`off` | `deny-list` |
    `catalog` | `strict`, по умолчанию `catalog`). Валидация выполняется **до** проверки квоты
    и генерации секрета — при отказе ничего не сохраняется. Отказ возвращает **422** с полями
    `reason` (`UNKNOWN_SCOPE` | `DENIED_SCOPE` | `NOT_GRANTABLE`) и `invalidScopes`.
    Подробнее — [Режимы валидации скоупов](../configuration.md#режимы-валидации-скоупов).

### `GET /api/openapi/tokens` — список своих токенов

```http
GET /api/openapi/tokens HTTP/1.1
Authorization: Basic ZGVtbzpkZW1v
```

```json
[
  {
    "id": "0f9a1b7c-…",
    "name": "ci-bot",
    "description": "Токен для CI",
    "prefix": "9f3c1a7b",
    "ownerId": "demo",
    "tenantId": "tenant-a",
    "status": "ACTIVE",
    "effectiveStatus": "ACTIVE",
    "scopes": ["payments:read"],
    "expiresAt": "2027-01-01T00:00:00Z",
    "lastUsedAt": "2026-10-07T11:05:00Z",
    "slidingTtlSeconds": 3600,
    "rateLimitRequests": 100,
    "rateLimitWindowSeconds": 60,
    "createdBy": "demo",
    "createdAt": "2026-10-07T11:00:00Z",
    "revokedAt": null
  }
]
```

Возвращаются **все** токены владельца, включая `REVOKED` и `BLOCKED`. Пагинации нет.

### `GET /api/openapi/tokens/{id}` — карточка токена

Возвращает `ApiTokenResponse`. Чужой токен → **404** (существование не раскрывается).

### `DELETE /api/openapi/tokens/{id}` — отозвать свой токен

```http
DELETE /api/openapi/tokens/0f9a1b7c-… HTTP/1.1
Authorization: Basic ZGVtbzpkZW1v
```

**204 No Content** при успехе. Тело не возвращается — если нужно показать новый статус,
запросите карточку после отзыва (или используйте UI, который делает это сам).

Операция **идемпотентна**: повторный `DELETE` уже отозванного токена тоже возвращает
**204 No Content** (а не 400) — безопасно для ретраев клиента. Для несуществующего токена —
**404**.

### `GET /api/openapi/tokens/{id}/stats` — статистика использования

```json
{
  "tokenId": "0f9a1b7c-…",
  "totalRequests": 128,
  "successfulRequests": 120,
  "failedRequests": 8,
  "lastUsedAt": "2026-10-07T11:05:00Z"
}
```

### `GET /api/openapi/tokens/{id}/audit` — журнал аудита

Возвращает страницу событий, отсортированных по времени убыванию, в виде объектов
`AuditEventResponse` (не доменных записей).

| Параметр | По умолчанию | Ограничения |
|---|---|---|
| `offset` | `0` | `@Min(0)` |
| `limit` | `50` | `@Min(1)` `@Max(500)` |

Действия в журнале: `AUTHENTICATE`, `CREATE_TOKEN`, `REVOKE_TOKEN`, `BLOCK_TOKEN`,
`UNBLOCK_TOKEN`.

```json
[
  {
    "tokenId": "0f9a1b7c-…",
    "ownerId": "demo",
    "tenantId": "tenant-a",
    "httpMethod": "GET",
    "endpoint": "/api/demo/payments",
    "action": "AUTHENTICATE",
    "success": true,
    "responseStatus": 200,
    "ip": "127.0.0.1",
    "userAgent": "curl/8.7.1",
    "createdAt": "2026-10-07T11:05:00Z"
  }
]
```

---

## Администрирование

Требуется authority `ROLE_ADMIN`: готовая цепочка статера применяет `hasRole("ADMIN")` к
`/api/openapi/admin/**`, а сам контроллер дополнительно помечен
`@PreAuthorize("hasRole('ADMIN')")` (defense in depth; аннотация действует, когда приложение
включает method security).

| Метод | Путь | Описание | Ответ |
|---|---|---|---|
| `GET` | `/api/openapi/admin/tokens?offset=0&limit=100` | Страница токенов всех владельцев (новые первыми) | `200` + JSON-массив `ApiTokenResponse` и заголовок `X-Total-Count` |
| `GET` | `/api/openapi/admin/tokens/{id}` | Карточка любого токена | `200` + `ApiTokenResponse`, `404` если нет |
| `GET` | `/api/openapi/admin/tokens/{id}/audit?offset=&limit=` | Аудит любого токена | `200` + JSON-массив `AuditEventResponse` |
| `DELETE` | `/api/openapi/admin/tokens/{id}` | Принудительный отзыв | `204` |
| `POST` | `/api/openapi/admin/tokens/{id}/block` | Блокировка | `200` + `ApiTokenResponse` (`status: BLOCKED`) |
| `POST` | `/api/openapi/admin/tokens/{id}/unblock` | Разблокировка | `200` + `ApiTokenResponse` (`status: ACTIVE`) |

Параметры пагинации: `offset` (`@Min(0)`, по умолчанию `0`) и `limit`
(`@Min(1)`, `@Max(500)`; по умолчанию `100` для списка токенов и аудита администратора,
`50` — для аудита владельца). Общее число токенов отдаётся заголовком `X-Total-Count`:

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Total-Count: 42

[ { "id": "0f9a1b7c-…", "name": "ci-bot", … } ]
```

!!! note "Серверной фильтрации нет"
    `GET /api/openapi/admin/tokens` поддерживает только постраничную выдачу, без фильтрации
    по владельцу/статусу/тексту. Фильтры, которые видны в демо-UI (`/admin/tokens`),
    выполняются в памяти на стороне контроллера UI.

```bash
# блокировка
curl -u admin:admin -X POST http://localhost:8080/api/openapi/admin/tokens/<id>/block

# принудительный отзыв
curl -u admin:admin -X DELETE http://localhost:8080/api/openapi/admin/tokens/<id>

# все токены
curl -u admin:admin http://localhost:8080/api/openapi/admin/tokens
```

Ограничения переходов: блокировать можно только `ACTIVE`, разблокировать — только `BLOCKED`,
иначе **400** (`Only ACTIVE tokens can be blocked` / `Only BLOCKED tokens can be unblocked`).

---

## Справочник скоупов

### `GET /api/openapi/scopes` — список известных скоупов

```json
[
  {
    "scope": "payments:read",
    "description": "Чтение платежей — открывает GET /api/demo/payments",
    "projectScopes": []
  },
  {
    "scope": "payment",
    "description": null,
    "projectScopes": ["createPayment", "readPayment"]
  }
]
```

| Поле | Тип | Описание |
|---|---|---|
| `scope` | `string` | Имя скоупа |
| `description` | `string \| null` | Человекочитаемое описание (`null`, если не задано) |
| `projectScopes` | `string[]` | Права приложения, которые даёт скоуп в режиме `mapped`; в `identity` — пусто |

Список отсортирован по имени, содержит объединение описанных скоупов и скоупов,
известных из маппингов. Подробнее: [Скоупы и права](../features/scopes.md).

---

## Схемы данных

### `ApiTokenResponse`

| Поле | Тип | Null |
|---|---|---|
| `id` | `UUID` | ✗ |
| `name` | `string` | ✗ |
| `description` | `string` | ✔ |
| `prefix` | `string` | ✗ |
| `ownerId` | `string` | ✗ |
| `tenantId` | `string` | ✔ |
| `status` | `string` (`ACTIVE`/`BLOCKED`/`REVOKED`/`EXPIRED`) | ✗ |
| `effectiveStatus` | `string` (`ACTIVE`/`BLOCKED`/`REVOKED`/`EXPIRED`) | ✗ |
| `scopes` | `string[]` | ✗ |
| `expiresAt` | `string (ISO-8601)` | ✔ |
| `lastUsedAt` | `string (ISO-8601)` | ✔ |
| `slidingTtlSeconds` | `integer` | ✔ |
| `rateLimitRequests` | `integer` | ✔ |
| `rateLimitWindowSeconds` | `integer` | ✔ |
| `createdBy` | `string` | ✔ |
| `createdAt` | `string (ISO-8601)` | ✗ |
| `revokedAt` | `string (ISO-8601)` | ✔ |

!!! note "`effectiveStatus` вычисляется на лету"
    `status` — состояние, хранимое в БД: просроченный токен остаётся там `ACTIVE`. Поле
    `effectiveStatus` считает `EXPIRED` для `ACTIVE`-токена с прошедшим `expiresAt`; для
    остальных статусов оно совпадает со `status`. Клиентам, которые показывают состояние
    пользователю, следует опираться на `effectiveStatus`.

### `CreatedTokenResponse`

| Поле | Тип | Описание |
|---|---|---|
| `token` | `ApiTokenResponse` | Метаданные созданного токена |
| `rawToken` | `string` | Секрет, отображается один раз |

### `CreateTokenRequest`

См. таблицу полей в разделе [создание токена](#post-apiopenapitokens--создать-токен).

### `TokenUsageStatsResponse`

| Поле | Тип | Описание |
|---|---|---|
| `tokenId` | `UUID` | Идентификатор токена |
| `totalRequests` | `number` | Всего событий аутентификации |
| `successfulRequests` | `number` | Успешных |
| `failedRequests` | `number` | Неуспешных (`total - successful`) |
| `lastUsedAt` | `string \| null` | Время последнего **события** (включая неуспешные) |

### `ScopeInfoResponse`

| Поле | Тип |
|---|---|
| `scope` | `string` |
| `description` | `string \| null` |
| `projectScopes` | `string[]` |

### Событие аудита

| Поле | Тип | Null |
|---|---|---|
| `tokenId` | `UUID` | ✔ |
| `ownerId` | `string` | ✔ |
| `tenantId` | `string` | ✔ |
| `httpMethod` | `string` | ✔ |
| `endpoint` | `string` | ✔ |
| `action` | `string` (`AUTHENTICATE`/`CREATE_TOKEN`/`REVOKE_TOKEN`/`BLOCK_TOKEN`/`UNBLOCK_TOKEN`) | ✔ |
| `success` | `boolean` | ✗ |
| `responseStatus` | `number` | ✔ |
| `ip` | `string` | ✔ |
| `userAgent` | `string` | ✔ |
| `createdAt` | `string (ISO-8601)` | ✗ |

---

## Ошибки

Все ошибки MVC-слоя возвращаются в формате **RFC 7807 `ProblemDetail`**
(`application/problem+json`) обработчиком `ApiTokenExceptionHandler`.

| Исключение | HTTP | `title` | `detail` / свойства |
|---|---|---|---|
| `MethodArgumentNotValidException` | **400** | `Validation Failed` | Сообщение первой ошибки; свойство `errors` — map «поле → сообщение» |
| `ConstraintViolationException` | **400** | `Validation Failed` | Сообщение нарушения (валидация параметров запроса, например `limit`) |
| `HttpMessageNotReadableException` | **400** | `Malformed Request` | `Request body is missing or malformed` (в том числе неизвестное поле JSON) |
| `MethodArgumentTypeMismatchException` | **400** | `Malformed Request` | `Parameter '{name}' has an invalid value` |
| `InvalidTokenStateException` | **400** | `Invalid Token Request` | `Only ACTIVE tokens can be blocked` / `Only BLOCKED tokens can be unblocked` / `Cannot touch non-active token` |
| `EmptyScopesException` | **400** | `Invalid Token Request` | `Token must have at least one scope` |
| `IllegalArgumentException` | **400** | `Invalid Token Request` | Сообщение из домена (например, про `slidingTtl` или `prefixLength`) |
| `TenantNotAllowedException` | **403** | `Tenant Not Allowed` | `Tenant override is not allowed: requested '{requested}', owner tenant '{owner}'` |
| `ApiTokenNotFoundException` | **404** | `API Token Not Found` | `API token not found: {id}` (в том числе чужой токен — существование не раскрывается) |
| `QuotaExceededException` | **409** | `Token Quota Exceeded` | `Token quota exceeded for owner '{owner}': max {N}` |
| `ConcurrentTokenModificationException` | **409** | `Concurrent Modification` | Конкурентное изменение токена (optimistic locking) |
| `InvalidScopeException` | **422** | `Invalid Scopes` | Свойства `reason` (`UNKNOWN_SCOPE`\|`DENIED_SCOPE`\|`NOT_GRANTABLE`) и `invalidScopes` |

Итого по кодам: **201** — создание, **400** — ошибки валидации/формата тела/неизвестное поле,
**403** — запрет tenant-override, **404** — токен не найден, **409** — квота или конкурентное
изменение, **422** — недопустимые скоупы.

```json
{
  "type": "about:blank",
  "title": "Token Quota Exceeded",
  "status": 409,
  "detail": "Token quota exceeded for owner 'demo': max 10"
}
```

Пример 400 с картой ошибок валидации:

```json
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "must not be empty",
  "errors": {
    "name": "must not be blank",
    "scopes": "must not be empty"
  }
}
```

Пример 422 при отклонённых скоупах:

```json
{
  "type": "about:blank",
  "title": "Invalid Scopes",
  "status": 422,
  "detail": "Invalid scopes (UNKNOWN_SCOPE): [payments:write]",
  "reason": "UNKNOWN_SCOPE",
  "invalidScopes": ["payments:write"]
}
```

### Ошибки аутентификации на уровне фильтра

`ApiTokenAuthenticationFilter` отвечает сам (не через MVC-обработчик), но в том же
`application/problem+json` и с корректными заголовками:

| Ситуация | HTTP | Заголовок | Тело |
|---|---|---|---|
| Токен отсутствует / не начинается с `Bearer atk_` | запрос идёт дальше по цепочке; если других механизмов нет — 401/403 от Spring Security | — | — |
| Неверный формат, неизвестный префикс, неверный секрет, статус не `ACTIVE`, истёк TTL, пустые authority | **401** | `WWW-Authenticate: Bearer` | `{"status":401,"title":"Unauthorized","detail":"Unauthorized"}` |
| Превышен rate limit (per-token или пред-аутентификационный) | **429** | `Retry-After: {rate-limit.retry-after-seconds}` | `{"status":429,"title":"Too Many Requests","detail":"Rate limit exceeded"}` |

!!! note "Причина пустых authority"
    При `openapi.tokens.auth.reject-empty-authorities=true` (по умолчанию) токен, который
    после резолва скоупов не даёт **ни одной** authority, не аутентифицируется → **401**.
    Это защита от «пустого» принципала, а не ошибка клиента.

### Необработанные ошибки

| Ситуация | Результат |
|---|---|
| Нарушение уникальности `prefix` (крайне маловероятно) | `DataIntegrityViolationException` → **500** |
| Недоступна БД | **500** |
| `prefix-length > 16` | `IllegalArgumentException` из `RawToken.generate` → **400** |

---

## Полный пример сценария

```bash
BASE=http://localhost:8080

# 1. Создать токен (владелец demo)
RESPONSE=$(curl -s -u demo:demo -H 'Content-Type: application/json' \
  -d '{"name":"ci-bot","scopes":["payments:read"],"rateLimitRequests":100,"rateLimitWindowSeconds":60}' \
  "$BASE/api/openapi/tokens")

TOKEN_ID=$(echo "$RESPONSE" | jq -r .token.id)
RAW=$(echo "$RESPONSE" | jq -r .rawToken)
echo "id=$TOKEN_ID prefix=$(echo "$RESPONSE" | jq -r .token.prefix)"

# 2. Использовать токен
curl -s -H "Authorization: Bearer $RAW" "$BASE/api/demo/payments"

# 3. Посмотреть статистику и аудит
curl -s -u demo:demo "$BASE/api/openapi/tokens/$TOKEN_ID/stats"
curl -s -u demo:demo "$BASE/api/openapi/tokens/$TOKEN_ID/audit"

# 4. Отозвать
curl -s -o /dev/null -w '%{http_code}\n' -u demo:demo -X DELETE "$BASE/api/openapi/tokens/$TOKEN_ID"   # 204

# 5. Проверить, что токен мёртв
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $RAW" "$BASE/api/demo/payments"     # 401
```

## Связанные разделы

- [Фронтенд-разработчику](../guides/frontend.md) — те же контракты глазами UI.
- [Java API](java-api.md) — альтернатива REST для внутренних вызовов.
- [Жизненный цикл токена](../features/token-lifecycle.md) — правила переходов статусов.
- [Конфигурация](../configuration.md) — как менять `header`/`bearer-prefix` и защиту `/api/openapi/**`.
