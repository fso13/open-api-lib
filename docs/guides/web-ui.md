# Web UI (server-rendered)

В репозитории есть готовый **серверный интерфейс** на Thymeleaf для пользователя и
администратора. Он не является частью библиотеки-статера: это референсная реализация,
которую можно переиспользовать или взять за образец для собственного UI.

| Что | Где |
|---|---|
| Демо-приложение (режим `internal`): конфигурация, security, демо-API, форма входа | `openapi-tokens-sample` |
| Библиотека UI: контроллеры, шаблоны, CSS, view-модели, advice'ы | `openapi-tokens-sample-ui` |
| Демо-приложение (режим `keycloak`): OIDC-вход, маппинг ролей, те же страницы | `openapi-tokens-sample-keycloak` |
| Пакет | **общий** — `ru.openapi.tokens.sample.ui` в этих модулях |

!!! warning "Один пакет — два модуля"
    `UiSecurityConfig` и `LoginViewController` остались в `openapi-tokens-sample`,
    а все страницы, шаблоны, CSS и мапперы живут в `openapi-tokens-sample-ui`, при этом
    пакет у них общий. При поиске «где код UI» проверяйте оба модуля.

Запуск:

```bash
docker compose up -d
./gradlew :openapi-tokens-sample:bootRun
```

Пользователи: `demo`/`demo` (`USER`), `admin`/`admin` (`ADMIN`, `USER`).
Вход: <http://localhost:8080/login>.

---

## 1. Маршруты

| Метод | Путь | Контроллер → шаблон | Доступ | Действие / редирект |
|---|---|---|---|---|
| `GET` | `/login` | `LoginViewController` → `login` | публичный | форма входа |
| `POST` | `/login` | Spring Security | публичный | `/` при успехе, `/login?error` при ошибке |
| `POST` | `/logout` | Spring Security | публичный + CSRF | `/login?logout` |
| `GET` | `/` | `HomeController` | аутентифицированный | `admin` → `/admin/tokens`, иначе → `/ui/tokens` |
| `GET` | `/ui/tokens` | `UserTokenViewController#list` → `ui/tokens` | аутентифицированный | список своих токенов (`q`, `status`) |
| `GET` | `/ui/tokens/new` | `#createForm` → `ui/token-new` | аутентифицированный | форма создания |
| `POST` | `/ui/tokens` | `#create` | + CSRF | `/ui/tokens/created` (+ flash `token`, `rawToken`) |
| `GET` | `/ui/tokens/created` | `#created` → `ui/token-created` | аутентифицированный | одноразовый показ секрета; без flash → `/ui/tokens` |
| `GET` | `/ui/tokens/{id}` | `#detail` → `ui/token-detail` | владелец | карточка: статистика + аудит (50) |
| `POST` | `/ui/tokens/{id}/revoke` | `#revoke` | владелец + CSRF | `/ui/tokens/{id}` + flash «Токен отозван» |
| `GET` | `/ui/scopes` | `ScopeCatalogViewController` → `ui/scopes` | аутентифицированный | справочник скоупов |
| `GET` | `/admin/tokens` | `AdminTokenViewController#list` → `admin/tokens` | `ROLE_ADMIN` | все токены (`q`, `owner`, `status`) |
| `GET` | `/admin/tokens/{id}` | `#detail` → `admin/token-detail` | `ROLE_ADMIN` | карточка любого токена (аудит 100) |
| `POST` | `/admin/tokens/{id}/block` | `#block` | `ROLE_ADMIN` + CSRF | карточка + flash «Токен заблокирован» |
| `POST` | `/admin/tokens/{id}/unblock` | `#unblock` | `ROLE_ADMIN` + CSRF | карточка + flash «Токен разблокирован» |
| `POST` | `/admin/tokens/{id}/revoke` | `#forceRevoke` | `ROLE_ADMIN` + CSRF | карточка + flash «Токен отозван администратором» |
| `GET` | `/css/app.css` | статика | задумано публичным | см. [известные особенности](#7-известные-особенности) |

Страницы ошибок: `error` (общая), `error/403` (доступ запрещён, с формой смены пользователя),
`error/404` (страница/токен не найдены).

---

## 2. Цепочки безопасности

В демо-приложении три `SecurityFilterChain`:

| # | Бин | Область | Механизм | CSRF |
|---|---|---|---|---|
| 1 | `openapiTokensSecurityFilterChain` (`@Order(1)`, из статера) | `/api/openapi/**` | HTTP Basic + фильтр API-токенов; `/api/openapi/admin/**` → `ROLE_ADMIN` | отключён |
| 2 | `uiSecurityFilterChain` (`@Order(2)`, `TokenUiSecurity.uiChain`) | `/`, `/ui/**`, `/admin/**`, `/login`, `/logout` | form login (`/login`), сессия | **включён** |
| 3 | `appSecurityFilterChain` (низший приоритет, без matcher) | всё остальное (`/api/demo/**`, `/error`, `/css/**`) | HTTP Basic, stateless | отключён |

- `TokenUiSecurity.uiChain(...)` — общий конструктор цепочки UI, вынесенный в модуль
  `openapi-tokens-sample-ui`, чтобы продукт мог переиспользовать те же правила.
- Константа `TokenUiSecurity.UI_FILTER_CHAIN_ORDER = 2`.
- `/admin/**` защищён дважды: на уровне URL и на уровне метода
  (`@PreAuthorize("hasRole('ADMIN')")` на `AdminTokenViewController`).
- Пароли в памяти хранятся как `{bcrypt}`-хеши (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`).

!!! note "`@EnableMethodSecurity` живёт в демо-приложении"
    `@PreAuthorize("hasRole('ADMIN')")` на админском контроллере работает только потому,
    что в `SampleSecurityConfig` включён `@EnableMethodSecurity`. Если переиспользовать
    модуль UI без этой аннотации, останется лишь защита на уровне URL.

---

## 3. CSRF

- Все POST-формы используют `th:action`, поэтому Thymeleaf автоматически добавляет скрытое
  поле `_csrf`. **Рукописных `_csrf`-полей в шаблонах нет.**
- POST без CSRF-токена → **403** (проверено тестами).
- Опасные действия подтверждаются `onsubmit="return confirm('…')"`:
  отзыв владельцем, блокировка, принудительный отзыв.
- GET-формы фильтров (`/ui/tokens`, `/admin/tokens`) тоже получают `_csrf` как побочный
  эффект `th:action` — токен оказывается в query-строке. Безвредно, но шумно; при
  переиспользовании лучше использовать обычный `action`.

---

## 4. Форма создания токена

Бэкенд-бин формы — `CreateTokenForm` (record). Валидация и сообщения (по-русски):

| Поле | Тип | Валидация | Сообщение | Виджет |
|---|---|---|---|---|
| `name` | `String` | `@NotBlank`, `@Size(max=128)` | «Укажите название токена», «Название не длиннее 128 символов» | `input[type=text]`, `autofocus` |
| `description` | `String` | `@Size(max=512)` | «Описание не длиннее 512 символов» | `textarea` |
| `scopes` | `String` | `@NotBlank` + проверка «после разбора непусто» | «Укажите хотя бы один scope» | `textarea` + чипы |
| `expiresAt` | `String` | проверка разбора даты | «Неверная дата и время» | `input[type=datetime-local]` |
| `slidingTtlSeconds` | `Integer` | `@Min(30)` | «Sliding TTL — минимум 30 секунд» | `input[type=number]` |
| `rateLimitRequests` | `Integer` | `@Min(1)` + правило «оба или ни одного» | «Лимит запросов — минимум 1», «Заполните оба поля лимита или оставьте оба пустыми» | `input[type=number]` |
| `rateLimitWindowSeconds` | `Integer` | `@Min(1)` | «Окно лимита — минимум 1 секунда» | `input[type=number]` |
| `tenantId` | `String` | `@Size(max=64)` | «tenantId не длиннее 64 символов» | `input[type=text]` |

Особенности:

- Скоупы вводятся свободным текстом и разбираются по разделителям `[,\s]+`;
  клики по чипам (`type="button"`) дописывают скоуп в поле через inline-JS.
- `expiresAt` интерпретируется в зоне бина `Clock` продукта.
- При ошибке валидации форма перерисовывается (HTTP 200), введённые значения сохраняются.
- Ограничения UI отличаются от API: `slidingTtlSeconds` в API допускает любое
  положительное значение, а в форме — минимум 30 секунд.

---

## 5. View-модели (контракт «контроллер → шаблон»)

В шаблоны **не попадают** доменные объекты — только record-модели из
`ru.openapi.tokens.sample.ui`:

| Модель | Назначение | Ключевые поля |
|---|---|---|
| `TokenView` | Строка списка и карточка | `id`, `name`, `description`, `prefix`, `ownerId`, `tenantId`, `status`, `statusLabel`, `statusCss`, `scopeLabel`, `createdAtLabel`, `expiresAtLabel`, `expiringSoon`, `lastUsedAtLabel`, `revokedAtLabel`, `slidingTtlLabel`, `rateLimitLabel` |
| `TokenUsageView` | Блок статистики | `totalRequests`, `successfulRequests`, `failedRequests`, `failureRateLabel`, `lastUsedAtLabel` |
| `AuditEntryView` | Строка журнала | `createdAtLabel`, `action`, `success`, `resultLabel`, `requestLabel`, `responseStatus`, `ip`, `userAgent` |
| `TokenCounters` | Счётчики статусов | `total`, `active`, `blocked`, `revoked`, `expired`; `TokenCounters.of(tokens)` |
| `TokenFilter` | Фильтр списка | `q`, `owner`, `status`, `apply(tokens)`, `matches(token)`, `statusOrDefault()` |
| `StatusOption` | Опция фильтра статуса | `value`, `label` |
| `ScopeView` | Строка справочника | `scope`, `description`, `authoritiesLabel` |
| `CreateTokenForm` | Форма создания | см. раздел 4 |

`TokenUiMapper` — `@Component` с внедрённым `Clock`: форматирует даты `dd.MM.yyyy HH:mm`,
считает «истекает скоро» (порог 3 дня), переводит `Duration` в человекочитаемый вид
(`N сут` / `N ч` / `N мин` / `N сек`), разбирает скоупы и дату.

Модельные атрибуты по страницам:

| Страница | Атрибуты модели |
|---|---|
| `ui/tokens` | `tokens`, `filter`, `counters`, `statusOptions` |
| `ui/token-new` | `form`, `scopes` |
| `ui/token-created` | flash `token`, `rawToken` |
| `ui/token-detail` | `token`, `usage`, `audit` |
| `ui/scopes` | `scopes`, `scopesMode` |
| `admin/tokens` | `tokens`, `filter`, `counters`, `statusOptions`, `owners` |
| `admin/token-detail` | `token`, `usage`, `audit` |
| все UI-страницы (через `UiModelAdvice`) | `currentUser`, `isAdmin`, `currentPath` |

---

## 6. Клиентский код и оформление

**JavaScript — два небольших inline-блока** (всего ~40 строк, без библиотек):

1. `ui/token-new.html` — клик по чипу дописывает скоуп в `textarea#scopes`.
2. `ui/token-created.html` — копирование секрета: `navigator.clipboard.writeText`,
   при отсутствии — `document.execCommand('copy')`; через 2 секунды надпись кнопки возвращается.

Плюс нативные `confirm(...)` на опасных формах.

| Аспект | Реализация |
|---|---|
| CDN | **нет** (ни одного внешнего `<script>`/`<link>`, favicon — data-URI) |
| CSS | один локальный файл `static/css/app.css`: CSS-переменные (дизайн-токены), без фреймворков, один breakpoint `@media (max-width: 720px)` |
| Тёмная тема | отсутствует |
| Запросы к REST из браузера | **отсутствуют** (нет `fetch`/XHR; всё рендерит сервер) |
| i18n | отсутствует; все тексты захардкожены по-русски |
| Автообновление | нет |

!!! note "Страница `/ui/scopes` формально не ходит в REST"
    Текст на странице и в README утверждают, что данные приходят из
    `GET /api/openapi/scopes`, но контроллер берёт тот же бин `ScopeCatalog` напрямую.
    Функционально эквивалентно, фактически — нет.

---

## 7. Известные особенности

| # | Особенность | Влияние |
|---|---|---|
| 1 | `TokenUiSecurity.PUBLIC_PATHS = {"/css/**", "/error"}` не входит в `securityMatchers` UI-цепочки | В реальном приложении `/css/app.css` и `/error` перехватываются catch-all цепочкой (`anyRequest().authenticated()`) → возможен 401/редирект на логин для стилей. Тест `stylesheetIsPublic` этого не ловит (в срезе MockMvc несовпавшие пути не фильтруются). Лечится добавлением путей в `securityMatchers` или `WebSecurityCustomizer` |
| 2 | Страница `/ui/scopes` не включает фрагмент flash | Сообщения `success`/`error`, попавшие сюда, молча теряются |
| 3 | `UiExceptionAdvice` умеет вести только в два списка (`/ui/tokens`, `/admin/tokens`) | Ошибка при блокировке/разблокировке возвращает на список, а не в карточку |
| 4 | `ownerVisible`/`listPath` на странице карточки владельца не используются | Мёртвые атрибуты модели |
| 5 | Нет пагинации | `/admin/tokens` загружает все токены всех владельцев и фильтрует в памяти Java |
| 6 | `login.html` подключает фрагмент из другого модуля | Демо-приложение не может отрендерить логин без `openapi-tokens-sample-ui` |
| 7 | `TokenFilter.statusOrDefault()` возвращает `status` без нормализации | Неизвестное значение статуса молча превращается в «все» (`ALL`) |
| 8 | GET-формы содержат `_csrf` | CSRF-токен попадает в query-строку |
| 9 | Дублирование бинов `clock`/`TokenUiMapper` в тестах разных модулей | Хрупкое место: ранее приводило к `BeanDefinitionOverrideException` |

---

## 8. Как переиспользовать

=== "Целиком как библиотеку"

    ```kotlin
    // build.gradle.kts продукта
    dependencies {
        implementation(project(":openapi-tokens-spring-boot-starter"))
        implementation(project(":openapi-tokens-sample-ui"))   // или ваша копия модуля
    }
    ```

    Затем в конфигурации продукта:

    ```java
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity          // обязательно: нужно для @PreAuthorize на админском контроллере
    class ProductUiSecurityConfig {

        @Bean
        @Order(TokenUiSecurity.UI_FILTER_CHAIN_ORDER)
        SecurityFilterChain uiChain(HttpSecurity http) throws Exception {
            return TokenUiSecurity.uiChain(http, "/login");
        }
    }
    ```

    Продукт должен предоставить: свой `UserDetailsService`/OIDC, бин `Clock` (по желанию),
    шаблон `login`, а также позаботиться о публичности `/css/**`.

=== "Как образец для своего UI"

    Возьмите за основу:

    1. **Контракт моделей** (раздел 5) и `TokenUiMapper` — они уже изолируют домен от
       представления и показывают, какие поля нужны интерфейсу.
    2. **Правила формы** (раздел 4) — те же проверки стоит перенести на клиент.
    3. **Логику действий**: одноразовый показ секрета, подтверждения, flash-сообщения,
       защита по статусам (блокировать только активный, разблокировать только заблокированный).
    4. **Слой безопасности** (`TokenUiSecurity.uiChain`) как референс для своих правил.

=== "Собственный SPA"

    Используйте REST API и руководство [Фронтенд-разработчику](frontend.md):
    серверный UI в этом случае не нужен, но его UX-решения (счётчики, фильтры, подсказки
    по скоупам, одноразовый секрет) стоит повторить.

---

## 9. Тесты UI

| Тест | Что проверяет |
|---|---|
| `UserTokenViewControllerTest` (`@WebMvcTest`, 16 тестов) | Список и фильтр по статусу, создание токена с проверкой всех полей команды, ошибки валидации, квота, справочник скоупов в форме, карточка со статистикой и аудитом, чужой токен, отзыв, одноразовый показ секрета, отсутствие CSRF → 403 |
| `AdminTokenViewControllerTest` (`@WebMvcTest`, 12 тестов) | Список всех владельцев, фильтры по владельцу/тексту/статусу, карточка, блокировка/разблокировка/принудительный отзыв, недопустимый переход, обычный пользователь → 403, отсутствие CSRF → 403, пустой результат |
| `ScopeCatalogViewControllerTest` (`@WebMvcTest`, 3 теста) | Справочник с описаниями и правами, режим `identity`, пустой справочник, требование аутентификации |
| `UiAccessControlTest` (в демо-модуле, 9 тестов) | Публичный логин, редиректы по ролям, доступ к админке, доступ к справочнику, стили, 404 |

Тесты используют `@WebMvcTest` + `@MockitoBean`; фикстуры токенов лежат в
`src/testFixtures` модуля UI и переиспользуются в демо-модуле через `testFixtures(...)`.

## Связанные разделы

- [Фронтенд-разработчику](frontend.md) — интеграция по REST, если UI отдельный.
- [REST API](../api/rest-api.md) — контракты, используемые этими экранами.
- [Бэкенд-разработчику](backend.md) — конфигурация security и расширения.
- [Тестирование](../operations/testing.md) — как запускать UI-тесты.
