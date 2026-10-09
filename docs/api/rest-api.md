# REST API

Библиотека предоставляет три группы эндпоинтов. Все они **не имеют собственных
`@PreAuthorize`** — доступ регулируется `SecurityFilterChain` (своим в приложении или
готовым из статера).

| Группа | Базовый путь | Кто имеет доступ (цепочка статера) |
|---|---|---|
| Токены владельца | `/api/openapi/tokens` | любой аутентифицированный |
| Администрирование | `/api/openapi/admin/tokens` | authority `ROLE_ADMIN` |
| Справочник скоупов | `/api/openapi/scopes` | любой аутентифицированный |

Формат данных — JSON (`application/json`), кодировка UTF-8, время — ISO-8601 UTC
(например `2026-10-07T11:00:00Z`), идентификаторы — UUID.

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

    {
      "token": {
        "id": "0f9a1b7c-1a2b-4c3d-8e4f-5a6b7c8d9e0f",
        "name": "ci-bot",
        "description": "Токен для CI",
        "prefix": "9f3c1a7b",
        "ownerId": "demo",
        "tenantId": "tenant-a",
        "status": "ACTIVE",
        "scopes": ["payments:read"],
        "expiresAt": "2027-01-01T00:00:00Z",
        "lastUsedAt": null,
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
| `scopes` | `string[]` | ✔ | не пусто, элементы не пустые | Скоупы (см. [Скоупы](../features/scopes.md)) |
| `expiresAt` | `string (ISO-8601)` | ✗ | — | Абсолютный срок действия |
| `slidingTtlSeconds` | `integer` | ✗ | > 0 | Скользящее окно жизни |
| `rateLimitRequests` | `integer` | ✗ | — | Лимит запросов (нужен вместе со следующим) |
| `rateLimitWindowSeconds` | `integer` | ✗ | — | Окно лимита в секундах |
| `tenantId` | `string` | ✗ | — | Тенант; если не задан — берётся из владельца |

Поля ответа: см. [`ApiTokenResponse`](#схемы-данных) и `rawToken`.

!!! warning "Секрет показывается один раз"
    `rawToken` возвращается только этим ответом. В БД хранится хеш, восстановить токен нельзя.
    Ответ **не кешируйте** и не логируйте.

!!! danger "Скоупы не валидируются"
    Любая непустая строка принимается как скоуп. В режиме `identity` это позволяет выдать
    токену authority `ROLE_ADMIN` и получить доступ к админскому API. Обязательно
    ограничьте допустимые скоупы на стороне продукта — см.
    [Повышение привилегий](../features/security.md#повышение-привилегий-через-скоупы).

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
    "scopes": ["payments:read"],
    "expiresAt": "2027-01-01T00:00:00Z",
    "lastUsedAt": "2026-10-07T11:05:00Z",
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

Повторный отзыв → **400** (`Token already revoked`).

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

Возвращает до **50** последних событий, отсортированных по времени убыванию.
Параметров пагинации нет.

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

Требуется authority `ROLE_ADMIN` (готовая цепочка статера: `hasRole("ADMIN")`).

| Метод | Путь | Описание | Ответ |
|---|---|---|---|
| `GET` | `/api/openapi/admin/tokens` | Все токены всех владельцев | `200` + массив `ApiTokenResponse` |
| `GET` | `/api/openapi/admin/tokens/{id}` | Карточка любого токена | `200` + `ApiTokenResponse`, `404` если нет |
| `GET` | `/api/openapi/admin/tokens/{id}/audit` | Аудит любого токена, до **100** событий | `200` + массив событий |
| `DELETE` | `/api/openapi/admin/tokens/{id}` | Принудительный отзыв | `204` |
| `POST` | `/api/openapi/admin/tokens/{id}/block` | Блокировка | `200` + `ApiTokenResponse` (`status: BLOCKED`) |
| `POST` | `/api/openapi/admin/tokens/{id}/unblock` | Разблокировка | `200` + `ApiTokenResponse` (`status: ACTIVE`) |

!!! note "Фильтрации и пагинации нет"
    `GET /api/openapi/admin/tokens` отдаёт **все** токены сразу (`findAll()`), без серверной
    фильтрации по владельцу/статусу/тексту. Фильтры, которые видны в демо-UI
    (`/admin/tokens`), выполняются в памяти на стороне контроллера UI.

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
| `scopes` | `string[]` | ✗ |
| `expiresAt` | `string (ISO-8601)` | ✔ |
| `lastUsedAt` | `string (ISO-8601)` | ✔ |
| `createdAt` | `string (ISO-8601)` | ✗ |
| `revokedAt` | `string (ISO-8601)` | ✔ |

!!! note "`slidingTtlSeconds` и rate limit не возвращаются"
    В ответе нет `slidingTtlSeconds`, `rateLimitRequests`, `rateLimitWindowSeconds`, `createdBy`.
    Чтобы показать их в интерфейсе, потребуется расширить DTO (это изменение библиотеки).

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
| `action` | `string` (`AUTHENTICATE`) | ✔ |
| `success` | `boolean` | ✗ |
| `responseStatus` | `number` | ✔ |
| `ip` | `string` | ✔ |
| `userAgent` | `string` | ✔ |
| `createdAt` | `string (ISO-8601)` | ✗ |

---

## Ошибки

Все ошибки возвращаются в формате **RFC 7807 `ProblemDetail`**
(`application/problem+json`) обработчиком `ApiTokenExceptionHandler`.

| Исключение | HTTP | `title` | `detail` |
|---|---|---|---|
| `ApiTokenNotFoundException` | **404** | `API Token Not Found` | `API token not found: {id}` / `API token not found for prefix: {prefix}` |
| `QuotaExceededException` | **409** | `Token Quota Exceeded` | `Token quota exceeded for owner '{owner}': max {N}` |
| `InvalidTokenStateException` | **400** | `Invalid Token Request` | `Token already revoked` / `Only ACTIVE tokens can be blocked` / `Only BLOCKED tokens can be unblocked` / `Cannot touch non-active token` |
| `EmptyScopesException` | **400** | `Invalid Token Request` | `Token must have at least one scope` |
| `IllegalArgumentException` | **400** | `Invalid Token Request` | сообщение из домена (например, про `slidingTtl`) |
| `MethodArgumentNotValidException` | **400** | `Validation Failed` | сообщение **первой** ошибки валидации, иначе `Validation failed` |

```json
{
  "type": "about:blank",
  "title": "Token Quota Exceeded",
  "status": 409,
  "detail": "Token quota exceeded for owner 'demo': max 10"
}
```

### Ошибки аутентификации (не `ProblemDetail`)

Их формирует не MVC-обработчик, а `ApiTokenAuthenticationFilter` через `response.sendError`,
поэтому тело — стандартная страница ошибки контейнера, а не JSON:

| Ситуация | HTTP | Сообщение |
|---|---|---|
| Токен отсутствует / не начинается с `Bearer atk_` | запрос идёт дальше по цепочке; если других механизмов нет — 401/403 от Spring Security | — |
| Неверный формат, неизвестный префикс, неверный секрет, статус не `ACTIVE`, истёк TTL | **401** | `Unauthorized` |
| Превышен rate limit | **429** | `Rate limit exceeded` |

!!! note "Нет единого формата ошибок аутентификации"
    Продукту, которому нужен JSON и на 401/429, стоит заменить фильтр или настроить
    свой `AuthenticationEntryPoint`/`AccessDeniedHandler`.

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
