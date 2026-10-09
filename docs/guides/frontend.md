# Документация для фронтенд-разработчика

Раздел описывает интеграцию с библиотекой со стороны клиента: какие HTTP-контракты доступны,
как выглядит жизненный цикл токена в интерфейсе, какие сценарии нужно реализовать и на какие
особенности API закладываться.

!!! info "Готовая UI-реализация — только справочная"
    В репозитории есть server-rendered UI на Thymeleaf (демо-модуль), который **не использует
    JavaScript-фреймворк и не вызывает REST из браузера** — он рендерит HTML на сервере
    и обращается к бинам Java напрямую. Для SPA/мобильного клиента используйте REST API,
    описанный ниже. Референс полезен как источник UX-решений:
    [Web UI (server-side)](web-ui.md).

---

## 1. Базовые сведения

| Параметр | Значение |
|---|---|
| Базовый путь | `/api/openapi` |
| Формат | JSON (`application/json`, UTF-8) |
| Время | ISO-8601 UTC (`2026-10-07T11:00:00Z`) — приводите к локальному времени на клиенте |
| Идентификаторы | UUID (строки) |
| Аутентификация пользователя | зависит от продукта: cookie-сессия, HTTP Basic, Bearer-JWT (Keycloak) |
| Аутентификация токеном | `Authorization: Bearer atk_<prefix>_<secret>` |
| CSRF | для `/api/openapi/**` **отключён**; если ваш фронтенд ходит в `/ui/**`/`/admin/**` демо-UI — CSRF обязателен |
| CORS | **не настроен библиотекой** — см. [раздел 8](#8-cors-и-сессии) |
| Пагинация | отсутствует |

Заголовок и префикс схемы настраиваются (`openapi.tokens.token.header`,
`openapi.tokens.token.bearer-prefix`) — если продукт их изменил, клиент должен знать
фактические значения (например, через свою конфигурацию).

---

## 2. Операции, которые нужны интерфейсу

| Операция UI | HTTP | Путь | Примечание |
|---|---|---|---|
| Список моих токенов | `GET` | `/api/openapi/tokens` | Без пагинации; фильтры — на клиенте |
| Создать токен | `POST` | `/api/openapi/tokens` | **Секрет в ответе — один раз** |
| Карточка токена | `GET` | `/api/openapi/tokens/{id}` | Чужой токен → 404 |
| Отозвать токен | `DELETE` | `/api/openapi/tokens/{id}` | `204`; повторно → `400` |
| Статистика использования | `GET` | `/api/openapi/tokens/{id}/stats` | Всего/успешных/неуспешных |
| Журнал обращений | `GET` | `/api/openapi/tokens/{id}/audit` | ≤ 50 записей, без пагинации |
| Справочник скоупов | `GET` | `/api/openapi/scopes` | Для формы создания и подсказок |
| Все токены (админ) | `GET` | `/api/openapi/admin/tokens` | По всем владельцам |
| Любой токен (админ) | `GET` | `/api/openapi/admin/tokens/{id}` | `404` если нет |
| Аудит любого токена (админ) | `GET` | `/api/openapi/admin/tokens/{id}/audit` | ≤ 100 записей |
| Блокировать | `POST` | `/api/openapi/admin/tokens/{id}/block` | `200` + обновлённый токен |
| Разблокировать | `POST` | `/api/openapi/admin/tokens/{id}/unblock` | `200` |
| Принудительный отзыв | `DELETE` | `/api/openapi/admin/tokens/{id}` | `204` |

Полные схемы полей — [REST API](../api/rest-api.md).

---

## 3. Модель данных на клиенте

```ts
type TokenStatus = 'ACTIVE' | 'BLOCKED' | 'REVOKED' | 'EXPIRED';

interface ApiToken {
  id: string;
  name: string;
  description: string | null;
  prefix: string;          // публичная часть токена — можно показывать
  ownerId: string;
  tenantId: string | null;
  status: TokenStatus;
  scopes: string[];
  expiresAt: string | null;   // ISO-8601 UTC
  lastUsedAt: string | null;  // ISO-8601 UTC
  createdAt: string;
  revokedAt: string | null;
}

interface CreatedToken {
  token: ApiToken;
  rawToken: string;   // показать ОДИН раз, не логировать, не кешировать
}

interface TokenUsageStats {
  tokenId: string;
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  lastUsedAt: string | null;
}

interface AuditEntry {
  tokenId: string | null;
  ownerId: string | null;
  tenantId: string | null;
  httpMethod: string | null;
  endpoint: string | null;
  action: string;              // всегда "AUTHENTICATE"
  success: boolean;
  responseStatus: number | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
}

interface ScopeInfo {
  scope: string;
  description: string | null;
  projectScopes: string[];     // пусто в режиме identity
}

interface ProblemDetail {   // RFC 7807 — формат всех прикладных ошибок
  type?: string;
  title: string;
  status: number;
  detail: string;
}
```

!!! danger "Чего нет в ответе, но нужно интерфейсу"
    `ApiToken` **не содержит** `slidingTtlSeconds`, `rateLimitRequests`, `rateLimitWindowSeconds`,
    `createdBy`. Если в интерфейсе нужно показать «скользящий TTL» или «лимит запросов»,
    потребуется доработка библиотеки (расширение DTO) — согласуйте это заранее.

---

## 4. Создание токена и одноразовый секрет

### Запрос

```ts
const response = await fetch('/api/openapi/tokens', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',           // если аутентификация по cookie-сессии
  body: JSON.stringify({
    name: 'CI биллинга',
    description: 'Токен для пайплайна',
    scopes: ['payments:read'],
    expiresAt: '2027-01-01T00:00:00Z',   // необязательно
    slidingTtlSeconds: 3600,             // необязательно
    rateLimitRequests: 100,              // только вместе со следующим
    rateLimitWindowSeconds: 60,
    tenantId: null                       // необязательно
  })
});

if (response.status === 201) {
  const created: CreatedToken = await response.json();
  showSecretOnce(created.rawToken);      // единственный шанс показать секрет
}
```

### Правила формы (повторяют валидацию API)

| Поле | Обязательно | Ограничения | Подсказка UI |
|---|---|---|---|
| `name` | ✔ | не пусто, ≤ 128 символов | автофокус |
| `description` | ✖ | ≤ 512 | многострочное поле |
| `scopes` | ✔ | минимум один непустой | чипы/мультиселект из справочника |
| `expiresAt` | ✖ | ISO-8601; в будущем (проверка — на вашей стороне) | `datetime-local`, конвертируйте в UTC |
| `slidingTtlSeconds` | ✖ | > 0 (в демо-UI — минимум 30) | числовое поле, опционально |
| `rateLimitRequests` + `rateLimitWindowSeconds` | ✖ | **либо оба, либо ни одного** | одно поле «Лимит запросов» + окно |
| `tenantId` | ✖ | в демо-UI ≤ 64 символов (в API ограничения нет) | скрывать, если продукт не мультитенантный |

!!! warning "Ключевые UX-требования к выпуску токена"
    1. **Секрет показывается один раз.** Покажите его заметно, дайте кнопку «Скопировать»
       (`navigator.clipboard.writeText` с fallback на `document.execCommand('copy')`),
       предупредите, что восстановить его нельзя.
    2. **Не кешируйте и не логируйте секрет.** Не кладите его в `localStorage`, аналитику,
       URL-параметры и заголовки Referer.
    3. **Сброс формы.** После успешного создания уводите пользователя со страницы формы,
       чтобы повторная отправка не создала второй токен.

### Реакция на ошибки

| Код | `title` | Что показать пользователю |
|---|---|---|
| `409` | `Token Quota Exceeded` | «Достигнут лимит токенов. Отзовите неиспользуемые токены или обратитесь к администратору». Учитывайте: **отозванные токены тоже занимают квоту** |
| `400` | `Validation Failed` | Сообщение из `detail` относится к первому невалидному полю; сопоставление с полем — по смыслу или собственной валидацией до отправки |
| `400` | `Invalid Token Request` | «Некорректный запрос» + технический `detail` |
| `401` / `403` | — | Перевести пользователя на вход; сессия/права истекли |
| `5xx` | — | Общая ошибка, предложить повторить |

---

## 5. Экраны и сценарии

### 5.1 Экран «Мои токены»

- Список всех токенов владельца со статусом и счётчиками:
  Всего / Активных / Заблокировано / Отозвано / Истекло.
- Поиск по названию, префиксу и описанию — **на клиенте** (сервер не фильтрует).
- Фильтр по статусу — **на клиенте**.
- Сортировка по умолчанию — по дате создания (сначала новые).
- Пустое состояние: «Токены не найдены».
- Действия в строке: «Открыть карточку», «Создать токен».

!!! danger "Статус `EXPIRED` почти никогда не встречается"
    Просроченный токен остаётся со статусом `ACTIVE` в ответе API. Чтобы не обмануть
    пользователя, **вычисляйте эффективный статус на клиенте**:

    ```ts
    function effectiveStatus(token: ApiToken, now = Date.now()): TokenStatus | 'EXPIRED' {
      if (token.status !== 'ACTIVE') return token.status;

      if (token.expiresAt && Date.parse(token.expiresAt) <= now) return 'EXPIRED';

      // скользящий TTL на клиенте вычислить нельзя: поле не приходит в ответе.
      return 'ACTIVE';
    }
    ```

    Скользящее окно в ответе отсутствует — либо не показывайте «истекает скоро» для таких
    токенов, либо запросите расширение DTO.

- Полезная подсказка: «истекает в ближайшие 3 дня» — считайте от `expiresAt`
  (в демо-UI используется порог 3 дня).

### 5.2 Экран «Создание токена»

- Форма по правилам из [раздела 4](#4-создание-токена-и-одноразовый-секрет).
- Скоупы подтягиваются из `GET /api/openapi/scopes`:
  - показывайте `description` как подсказку, «описание не задано» — если `null`;
  - в режиме `mapped` (`projectScopes` непустой) полезно показать, какие права даёт скоуп;
  - если справочник пуст, дайте возможность ввести скоупы вручную и объясните,
    что их нужно описать в конфигурации/справочнике.
- Перед отправкой проверьте: «оба поля лимита заполнены или оба пусты».

### 5.3 Экран «Секрет создан» (одноразовый показ)

- Крупно секрет, кнопка «Скопировать» с подтверждением («Скопировано»).
- Готовый пример использования:
  `curl -H "Authorization: Bearer <секрет>" http://<host>/api/...`.
- Ссылки: «Открыть карточку токена», «К списку токенов».
- **Не оставляйте секрет в истории браузера**: показывайте его после `POST`-редиректа
  или в состоянии приложения, а не в URL.

### 5.4 Экран «Карточка токена» (владелец)

- Метаданные: название, описание, префикс, владелец (обычно не нужен), статус, скоупы,
  дата создания, срок действия, последнее использование, при отзыве — дата отзыва.
- Статистика: всего / успешных / неуспешных запросов, процент отказов, последнее использование.
- Журнал: последние ≤ 50 событий (время, результат, метод + эндпоинт, HTTP-статус, IP, User-Agent).
- Действие «Отозвать» — с подтверждением, предупреждением о необратимости
  и блокировкой кнопки после успеха.

!!! note "Два разных «последнее использование»"
    `ApiToken.lastUsedAt` обновляется только при **успешной** аутентификации,
    а `TokenUsageStats.lastUsedAt` — по **последнему событию**, включая отказы (401/429).
    Подписывайте их по-разному, чтобы не путать пользователя.

### 5.5 Административные экраны

| Экран | Данные | Действия |
|---|---|---|
| Все токены | `GET /api/openapi/admin/tokens`: все владельцы, счётчики | Фильтры по тексту/владельцу/статусу — **на клиенте** |
| Карточка (админ) | `GET .../admin/tokens/{id}`, `/stats`, `/audit` (≤ 100) | Блокировать (только `ACTIVE`), разблокировать (только `BLOCKED`), отозвать принудительно (кроме `REVOKED`) |

Рекомендации:

- кнопки действий показывайте по текущему статусу, чтобы не получать `400`
  (`Only ACTIVE tokens can be blocked`);
- при `Invalid Token State` обновляйте карточку — состояние изменилось в другой сессии;
- список владельцев стройте на клиенте: `[...new Set(tokens.map(t => t.ownerId))].sort()`.

---

## 6. Особенности API, влияющие на UX

| # | Особенность | Как учитывать во фронтенде |
|---|---|---|
| 1 | Нет пагинации и серверных фильтров | Загружайте весь список и фильтруйте локально; при больших объёмах инициируйте доработку API |
| 2 | Аудит ограничен 50/100 записями, без параметров | Не обещайте «всю историю»; подпишите «последние N записей» |
| 3 | `DELETE` не идемпотентен | Второй вызов вернёт `400`; считайте это «уже отозван» и не показывайте ошибку как сбой |
| 4 | 401/429 без JSON и без `Retry-After` | Для 429 покажите «слишком часто, попробуйте позже» без точного времени |
| 5 | Статус `EXPIRED` не выставляется | Вычисляйте эффективный статус (см. 5.1) |
| 6 | Квота учитывает отозванные токены | В сообщении об ошибке предлагайте «отозвать неиспользуемые» осторожно: освобождения квоты не будет |
| 7 | Ошибки 401/429 приходят как HTML-страница | Не парсите тело; ориентируйтесь на `response.status` |
| 8 | `rawToken` только в ответе на создание | Не пытайтесь получить секрет повторно — такой операции нет |
| 9 | Секрет не восстановим | Обязательно предупредите пользователя в UI |
| 10 | Токен-аутентификация не даёт CSRF-проблем для API | Для `/api/openapi/**` CSRF отключён; для UI-эндпоинтов демо — нет |

---

## 7. Пример клиента (TypeScript)

```ts
class OpenApiTokensClient {
  constructor(private baseUrl = '/api/openapi') {}

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      credentials: 'include',
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) }
    });

    if (response.status === 204) return undefined as T;

    if (!response.ok) {
      // Прикладные ошибки — ProblemDetail; 401/429 — без тела
      const problem = response.headers.get('content-type')?.includes('json')
        ? await response.json()
        : null;

      throw new TokenApiError(
        problem?.title ?? `HTTP ${response.status}`,
        problem?.detail ?? response.statusText,
        response.status
      );
    }

    return response.json() as Promise<T>;
  }

  listTokens(): Promise<ApiToken[]>              { return this.request('/tokens'); }
  getToken(id: string): Promise<ApiToken>        { return this.request(`/tokens/${id}`); }
  stats(id: string): Promise<TokenUsageStats>    { return this.request(`/tokens/${id}/stats`); }
  audit(id: string): Promise<AuditEntry[]>       { return this.request(`/tokens/${id}/audit`); }
  scopes(): Promise<ScopeInfo[]>                 { return this.request('/scopes'); }

  createToken(body: CreateTokenRequest): Promise<CreatedToken> {
    return this.request('/tokens', { method: 'POST', body: JSON.stringify(body) });
  }

  revokeToken(id: string): Promise<void> {
    return this.request(`/tokens/${id}`, { method: 'DELETE' });
  }

  // административные
  listAllTokens(): Promise<ApiToken[]>          { return this.request('/admin/tokens'); }
  block(id: string): Promise<ApiToken>          { return this.request(`/admin/tokens/${id}/block`, { method: 'POST' }); }
  unblock(id: string): Promise<ApiToken>        { return this.request(`/admin/tokens/${id}/unblock`, { method: 'POST' }); }
  forceRevoke(id: string): Promise<void>        { return this.request(`/admin/tokens/${id}`, { method: 'DELETE' }); }
}
```

---

## 8. CORS и сессии

!!! danger "CORS не настроен"
    Библиотека не добавляет ни одной CORS-настройки. Если фронтенд раздаётся с другого
    origin (SPA на отдельном домене/порту), браузер заблокирует запросы — нужна конфигурация
    на стороне продукта:

    ```java
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));   // явный список, не "*"
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);                               // если нужны cookie
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/openapi/**", config);
        return source;
    }
    ```

    Учтите: готовая цепочка безопасности статера для `/api/openapi/**` **не** включает CORS —
    либо добавьте `http.cors(...)` в свою цепочку (`openapiTokensSecurityFilterChain`),
    либо настройте CORS на уровне шлюза.

| Способ аутентификации | Что делать фронтенду |
|---|---|
| Cookie-сессия продукта | `credentials: 'include'`, поддерживать CSRF-защиту продукта, предусмотреть обработку 401 → редирект на вход |
| HTTP Basic | Не используйте в браузерных SPA: логин/пароль попадают в память и логи |
| Bearer-JWT (Keycloak) | Передавайте `Authorization: Bearer <jwt>`; обновляйте токен заранее |
| API-токен как учётные данные | `Authorization: Bearer atk_…` — подходит для серверных интеграций, не для UI |

---

## 9. Чек-лист фронтенд-интеграции

- [ ] Реализован одноразовый показ секрета с предупреждением и копированием.
- [ ] Секрет не попадает в логи, аналитику, `localStorage` и URL.
- [ ] Форма создания проверяет обязательные поля и правило «оба поля лимита или ни одного».
- [ ] Скоупы подтягиваются из справочника, описания отображаются (включая случай `null`).
- [ ] Эффективный статус токена рассчитывается на клиенте (учёт `expiresAt`).
- [ ] Фильтры и сортировка выполняются на клиенте (сервер их не поддерживает).
- [ ] Обрабатываются `400`, `404`, `409` через `ProblemDetail`, а `401/403/429` — по коду.
- [ ] Повторный `DELETE` (`400`) трактуется как «уже отозван».
- [ ] Кнопки блокировки/разблокировки показываются по статусу.
- [ ] Административные экраны недоступны пользователю без роли администратора.
- [ ] Настроен CORS (или фронтенд и API на одном origin).
- [ ] Дата/время из UTC приводятся к локальной зоне пользователя.

---

## 10. Связанные разделы

- [REST API](../api/rest-api.md) — полные контракты, примеры, коды ошибок.
- [Web UI (server-side)](web-ui.md) — референсный интерфейс и его особенности.
- [Жизненный цикл токена](../features/token-lifecycle.md) — статусы и правила.
- [Аудит и статистика](../features/audit.md) — содержимое журнала.
- [Скоупы и права](../features/scopes.md) — что показывать в справочнике.
