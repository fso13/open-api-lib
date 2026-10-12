# Тестирование

Раздел описывает, как устроены тесты проекта, как их запускать, что уже проверено и где
остаются пробелы.

---

## 1. Запуск тестов

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test
```

Полная сборка (компиляция, тесты, проверка покрытия) — одной командой:

```bash
./gradlew build
```

`build` включает `check`, а в `check` входят задачи `jacocoTestCoverageVerification`
с порогами по строкам покрытия (см. раздел 2.1) — падение порога ломает сборку.

Требования:

- **JDK 21** (toolchain зафиксирован в корневом `build.gradle.kts`);
- **Docker** — интеграционные тесты поднимают PostgreSQL 16 через Testcontainers;
- порт 5432 не обязателен (Testcontainers использует случайный), но Docker должен работать.

Отдельные модули:

```bash
./gradlew :openapi-tokens-core:test
./gradlew :openapi-tokens-web:test
./gradlew :openapi-tokens-persistence-jpa:test
./gradlew :openapi-tokens-sample:test
```

!!! note "Нет отдельной задачи `integrationTest`"
    Все тесты — включая классы с суффиксом `*IT` и Cucumber-раннеры — находятся в `src/test`
    и выполняются задачей `test`. Фильтрации по тегам нет.

Запуск с отчётом:

```bash
./gradlew test --info            # подробный вывод
open build/reports/tests/test/index.html
```

---

## 2. Стек тестирования

| Инструмент | Версия | Назначение |
|---|---|---|
| JUnit 5 | 5.11.x (BOM Boot 3.4.1) | Базовый раннер |
| AssertJ | 3.26.x | Утверждения |
| Mockito (+ `mockito-junit-jupiter`) | 5.14.x | Моки в юнит-тестах |
| Cucumber (`cucumber-java`, `cucumber-junit-platform-engine`) | 7.20.1 | BDD-сценарии |
| Testcontainers (`junit-jupiter`, `postgresql`) | 1.20.4 | Интеграционные тесты с реальной БД |
| ArchUnit (`archunit-junit5`) | 1.3.0 | Проверка архитектурных правил (модуль `autoconfigure`) |
| `spring-boot-starter-test`, `spring-security-test` | 3.4.1 | `@WebMvcTest`, `MockMvc`, `@WithMockUser` |
| Spring Boot Testcontainers | 3.4.1 | Интеграция контейнеров со Spring |
| JaCoCo (плагин Gradle) | — | Покрытие: отчёты + пороги (`check`) |

Дополнительно настроено в Gradle:

- `useJUnitPlatform()` для всех модулей;
- `environment("TESTCONTAINERS_RYUK_DISABLED", "true")` в `persistence-jpa` и `sample`;
- `src/test/resources/testcontainers.properties`: `checks.disable=true`,
  `ryuk.container.image=testcontainers/ryuk:0.11.0`;
- `src/test/resources/docker-java.properties`: `api.version=1.44`;
- компиляция с `-Xlint:all` (кроме `-serial`, `-processing`) и `-Werror` — предупреждения
  компилятора ломают сборку;
- `javadoc` с `isFailOnError = true` — битые javadoc-ссылки ломают сборку;
- `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` — в Gradle 9 лаунчер
  больше не подставляется автоматически.

### 2.1. Покрытие (JaCoCo)

Для каждого модуля выполняется `jacocoTestReport` (XML + HTML), а для модулей с порогом —
`jacocoTestCoverageVerification`:

| Модуль | Порог по строкам | Фактическое покрытие |
|---|---|---|
| `openapi-tokens-core` | 78 % | 84.2 % |
| `openapi-tokens-security` | 72 % | 76.1 % |
| `openapi-tokens-persistence-jpa` | 85 % | 90.6 % |
| `openapi-tokens-autoconfigure` | 62 % | 67.4 % |
| `openapi-tokens-web` | 45 % | 50.7 % |

Отчёты: `*/build/reports/jacoco/test/jacocoTestReport.xml` и
`*/build/reports/jacoco/test/html/index.html`.

!!! warning "Средства контроля качества подключены не полностью"
    JaCoCo и строгие компилятор/javadoc-настройки **есть**. Пока **нет** Spotless/Checkstyle/PMD
    (стиль), mutation-тестирования и dependency locking (фиксация версий).

---

## 3. Инвентаризация тестов

Последний зафиксированный прогон: **229 тестов, 0 падений** (артефакты
`*/build/test-results/test`).

| Модуль | Тестов | Что проверяется |
|---|---|---|
| `openapi-tokens-core` | 102 | Домен и инварианты, формат/генерация токена, TTL, хешеры, лимитер (включая LRU-эвикцию и политику лимитов), резолверы и **валидаторы скоупов**, справочник, асинхронный аудит, сервис жизненного цикла, Cucumber-сценарии |
| `openapi-tokens-security` | 26 | **Фильтр аутентификации** (пропуск, 401/429 `problem+json`, `SecurityContext`, аудит), порядок проверок в аутентификаторе (статус → TTL → хеш → лимит → скоупы), резолвер владельца Keycloak, резолвер authority вызывающего |
| `openapi-tokens-web` | 7 | Создание/список/отзыв/статистика токенов, блокировка, справочник скоупов (standalone `MockMvc` с `ApiTokenExceptionHandler`) |
| `openapi-tokens-autoconfigure` | 13 | Включение/выключение статера, наличие бинов, каталог и **валидация скоупов из конфигурации**, режимы `off`/`catalog`/`strict`, маппинг optimistic lock → 409, ArchUnit (4 правила) |
| `openapi-tokens-persistence-jpa` | 11 | JPA-адаптеры на PostgreSQL (Testcontainers): `@Version`, пагинация, обрезка длинных значений аудита |
| `openapi-tokens-sample` | 19 | E2E (`@SpringBootTest` + Testcontainers): выпуск токена и вызов защищённого API; **security hardening** (`TokenSecurityIT`: отказ по `ROLE_*`/неизвестному скоупу → 422, чужой тенант → 403, админка только для `ROLE_ADMIN`, 401 как `problem+json`); контроль доступа UI |
| `openapi-tokens-sample-ui` | 33 | Срезы контроллеров UI: список/фильтры/карточки/действия/валидация/CSRF, статические ресурсы |
| `openapi-tokens-sample-keycloak` | 18 | Keycloak JWT → владелец из `sub` и роли → `ROLE_*`, доступ по скоупу API-токена, админка по `ROLE_ADMIN`, редирект на Keycloak, конвертеры ролей/claim'ов (`SecurityMockMvcRequestPostProcessors.jwt()`, без живого Keycloak) |
| `openapi-tokens-spring-boot-starter` | 0 | Тестов нет |

### 3.1 BDD-сценарии (Cucumber)

Файлы: `openapi-tokens-core/src/test/resources/features/`.

| Feature | Сценарии | Правила |
|---|---|---|
| `features/scopes/scopes-resolution.feature` | 3 | BR-1, BR-2, BR-3 |
| `features/token/create-token.feature` | 2 | BR-4, BR-5 |
| `features/token/revoke-token.feature` | 1 | BR-6 |

Раннеры: `ScopesResolutionCucumberIT`, `TokenLifecycleCucumberIT` — `@Suite`
с `@IncludeEngines("cucumber")`, `@SelectClasspathResource("features/...")`,
glue-пакеты `ru.openapi.tokens.token.scope` и `ru.openapi.tokens.token.service`,
плагин `pretty`. Шаги собирают сервисы вручную на моках и фиксированном
`Clock` (`2026-10-07T12:00:00Z`).

### 3.2 Что покрыто по типам

| Тип | Где | Особенности |
|---|---|---|
| Юнит (без Spring) | `core`, `security` | Mockito + AssertJ, управляемое время через `MutableClock implements TimeMeter` |
| Срез MVC | `web` (standalone `MockMvc` + advice), `sample`/`sample-ui` (`@WebMvcTest` + `@MockitoBean`) | Контракты JSON, `ProblemDetail`, view-модели, CSRF, контроль доступа |
| Срез JPA | `persistence-jpa` (`@Testcontainers`) | Реальная схема после Flyway (`V1`–`V3`), `ddl-auto=validate` |
| Архитектура | `autoconfigure` (ArchUnit) | Изоляция `dto` ↔ `@Entity`, ядро без Spring/JPA/Servlet, internals персистентности |
| Контекст | `autoconfigure` (`ApplicationContextRunner`) | Условия `@ConditionalOnProperty`, наличие/отсутствие бинов, выбор `ScopeValidator` |
| E2E | `sample` (`@SpringBootTest` + Testcontainers) | Полный путь: выпуск токена, вызов API, отказы безопасности, 401/403/422 |

---

## 4. Пробелы в покрытии

!!! warning "Перечисленное ниже не проверяется или проверено частично"
    **Ранее непокрытое, теперь закрытое:**

    - `ApiTokenAuthenticationFilter` — покрыт `ApiTokenAuthenticationFilterTest` (401/429,
      пропуск запроса, `SecurityContext`, аудит);
    - порядок проверок аутентификатора — покрыт `DefaultApiTokenAuthenticatorTest`;
    - валидаторы скоупов — покрыты `ScopeValidatorTest` и `OpenApiTokensAutoConfigurationTest`;
    - готовая цепочка безопасности — покрыта e2e `TokenSecurityIT` (401/403, админка);
    - резолвер authority вызывающего — `SecurityContextCallerAuthorityResolverTest`.

    **Остаётся без полного покрытия:**

    **Безопасность:**

    1. `ApiTokenAuthenticationProvider` — покрыт лишь косвенно (через фильтр и e2e), прямых
       юнит-тестов на `BadCredentialsException`, неверный формат и rethrow лимита нет.
    2. `ApiTokenAuthentication` — набор authority и principal/credentials проверяются только
       через фильтр.
    3. `ApiTokenPermissionEvaluator` — оба `hasPermission`, `hasAnyAuthority`, анонимный случай.
    4. `InternalTokenOwnerResolver` — все ветки (владелец из токена, `UserDetails`, `String`,
       отсутствие аутентификации).

    **Web и конфигурация:**

    5. `ApiTokenWebMapper` — юнит-тестов преобразований нет (проверяется косвенно).
    6. `TransactionalApiTokenService` — границы транзакций не проверяются.
    7. Валидация `OpenApiTokensProperties` (`@Min`, `@NotBlank`) и ветки
       `auth.mode=keycloak`, `scopes.mode=mapped`, `hashing.algorithm=bcrypt` в
       `ApplicationContextRunner`, кастомные `token.header`/`bearer-prefix` — не покрыты
       (косвенно `auth.mode=keycloak` проверяется тестами семпла Keycloak).
    8. Модуль `spring-boot-starter` — тестов нет; `AutoConfiguration.imports` не проверяется.

    **Инфраструктура и НФТ:**

    9. **Redis-лимитер** — реализации не существует, тестировать нечего.
    10. **springdoc/OpenAPI** (`/v3/api-docs`) — не подключён.
    11. Мультитенантность: фильтрация `scope_mapping` по тенанту — на одном тенанте,
        фильтрации токенов по тенанту нет.
    12. Нет проверок энтропии токена, коллизий префикса и стойкости параметров хеширования.
    13. `ApiTokenRepository#findAll`, `usageStats` по отказам, `deleteByScope` для
        отсутствующего скоупа — не покрыты.
    14. Ротация/партиционирование журнала аудита — не реализованы, значит и не тестируются.

---

## 5. Как добавить тесты

### Юнит-тест ядра

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock ApiTokenRepository repository;
    @Mock TokenHasher hasher;
    @Mock TokenOwnerResolver ownerResolver;

    @Test
    void shouldCreateToken_WhenQuotaNotExceeded() {
        // Given
        when(ownerResolver.resolveCurrentOwner()).thenReturn(TokenOwner.of("owner-1"));
        when(hasher.hash(anyString())).thenReturn("hash");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new DefaultApiTokenService(
                repository, hasher, ownerResolver, Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC),
                8, 10);

        // When
        CreatedApiToken created = service.create(CreateTokenCommand.of("ci", Set.of("read")));

        // Then
        assertThat(created.rawToken().value()).startsWith("atk_");
        assertThat(created.token().status()).isEqualTo(TokenStatus.ACTIVE);
    }
}
```

### Срез MVC

```java
@WebMvcTest(controllers = UserApiTokenController.class)
class MyControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ApiTokenService service;
    @MockitoBean AuditLogRepository audit;
    @Autowired ApiTokenWebMapper mapper;   // или @Import(ApiTokenWebMapper.class)
    …
}
```

### Интеграционный тест

См. шаблон с `@Testcontainers` + `@DynamicPropertySource` в разделе
[Бэкенд-разработчику](../guides/backend.md#9-тестирование-интеграции).

### BDD-сценарий

1. Создайте `features/<область>/<имя>.feature` в `openapi-tokens-core/src/test/resources`.
2. Добавьте шаги в пакет-glue и раннер `@Suite` с `@SelectClasspathResource`.
3. Придерживайтесь правил: один сценарий — одно поведение; шаги — на бизнес-языке;
   в шагах не должно быть бизнес-логики (только подготовка и вызов).

---

## 6. Рекомендации по развитию тестов

| Приоритет | Что добавить | Зачем |
|---|---|---|
| 🔴 Высокий | Прямые тесты `ApiTokenAuthenticationProvider` и `ApiTokenPermissionEvaluator` | Частично непокрытый горячий путь безопасности |
| 🔴 Высокий | Прямые тесты `InternalTokenOwnerResolver` | Разные типы principal в проде |
| 🟠 Средний | Валидация свойств и ветки автоконфигурации (`keycloak`, `mapped`, `bcrypt`) | Защита от регрессий конфигурации |
| 🟠 Средний | BDD для REST- и UI-потоков | Требование правил проекта; сейчас только 3 feature-файла |
| 🟠 Средний | Тесты транзакционных границ `TransactionalApiTokenService` | Консистентность при ошибках |
| 🟡 Низкий | Тесты мультитенантности (фильтрация по `tenant_id`) | Требуется при мультитенантном продукте |
| 🟡 Низкий | Проверки энтропии/коллизий и параметров хеширования | Стойкость секретов |

---

## 7. Тестирование в CI

Настроены два пайплайна:

- **GitLab CI** — job `test` в `.gitlab-ci.yml`: образ
  `eclipse-temurin:21-jdk`, сервис `docker:dind`, `./gradlew --no-daemon build`
  (`build` включает пороги JaCoCo), артефакты — JUnit XML и отчёты JaCoCo;
- **GitHub Actions** — job `test` в `.github/workflows/build.yml`: `ubuntu-latest` с уже
  доступным Docker, JDK 21 (temurin), `./gradlew --no-daemon build`, выгрузка отчётов.

```yaml
# эквивалент для GitLab CI
test:
  image: eclipse-temurin:21-jdk
  services:
    - docker:dind            # Testcontainers требует Docker
  variables:
    TESTCONTAINERS_RYUK_DISABLED: "true"
  script:
    - ./gradlew build --no-daemon
  artifacts:
    when: always
    reports:
      junit: "*/build/test-results/test/*.xml"
    paths:
      - "*/build/reports/jacoco"
```

Учтите: `TESTCONTAINERS_RYUK_DISABLED=true` отключает «сборщик мусора» контейнеров,
поэтому упавший прогон может оставить контейнеры — при необходимости периодически чистите их.

## Связанные разделы

- [Ограничения и roadmap](roadmap.md) — что планируется исправить.
- [Публикация документации](publishing.md) — отдельный CI для сайта.
- [Бэкенд-разработчику](../guides/backend.md) — примеры интеграционных тестов.
