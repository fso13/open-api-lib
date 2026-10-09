# Тестирование

Раздел описывает, как устроены тесты проекта, как их запускать, что уже проверено и где
остаются пробелы.

---

## 1. Запуск тестов

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test
```

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
| ArchUnit (`archunit-junit5`) | 1.3.0 | Проверка архитектурных правил |
| `spring-boot-starter-test`, `spring-security-test` | 3.4.1 | `@WebMvcTest`, `MockMvc`, `@WithMockUser` |
| Spring Boot Testcontainers | 3.4.1 | Интеграция контейнеров со Spring |

Дополнительно настроено в Gradle:

- `useJUnitPlatform()` для всех модулей;
- `environment("TESTCONTAINERS_RYUK_DISABLED", "true")` в `persistence-jpa` и `sample`;
- `src/test/resources/testcontainers.properties`: `checks.disable=true`,
  `ryuk.container.image=testcontainers/ryuk:0.11.0`;
- `src/test/resources/docker-java.properties`: `api.version=1.44`.

!!! warning "Средства контроля качества не подключены"
    В проекте **нет** JaCoCo (измерения покрытия), Spotless/Checkstyle/PMD (стиль),
    mutation-тестирования, блокировок зависимостей и задачи с тегами. Заявление README
    о «coverage baseline» воспроизвести нельзя — счётчик не настроен.

---

## 3. Инвентаризация тестов

Последний зафиксированный прогон: **158 тестов, 0 падений** (артефакты
`*/build/test-results/test`).

| Модуль | Тестов | Что проверяется |
|---|---|---|
| `openapi-tokens-core` | 71 | Домен и инварианты, формат/генерация токена, TTL, хешеры, лимитер, резолверы скоупов, справочник, асинхронный аудит, сервис жизненного цикла, Cucumber-сценарии |
| `openapi-tokens-security` | 11 | Порядок проверок в аутентификаторе (статус → TTL → хеш → лимит → скоупы), резолвер владельца Keycloak (JWT, OIDC, кастомный claim) |
| `openapi-tokens-web` | 7 | Создание/список/отзыв/статистика токенов, блокировка, справочник скоупов (standalone `MockMvc`) |
| `openapi-tokens-autoconfigure` | 4 | Включение/выключение статера, наличие бинов, каталог скоупов из конфигурации |
| `openapi-tokens-persistence-jpa` | 6 | JPA-адаптеры на PostgreSQL (Testcontainers) + ArchUnit |
| `openapi-tokens-sample` | 10 | E2E-сценарий (создание токена + вызов защищённого API) и контроль доступа UI |
| `openapi-tokens-sample-ui` | 31 | Срезы контроллеров UI: список/фильтры/карточки/действия/валидация/CSRF |
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
| Срез MVC | `web` (standalone `MockMvc`), `sample`/`sample-ui` (`@WebMvcTest` + `@MockitoBean`) | Контракты JSON, view-модели, CSRF, контроль доступа |
| Срез JPA | `persistence-jpa` (`@DataJpaTest` + Testcontainers) | Реальная схема после Flyway, `ddl-auto=validate` |
| Архитектура | `persistence-jpa` (ArchUnit) | Изоляция `dto` ↔ `@Entity` |
| Контекст | `autoconfigure` (`ApplicationContextRunner`) | Условия `@ConditionalOnProperty`, наличие/отсутствие бинов |
| E2E | `sample` (`@SpringBootTest` + Testcontainers) | Happy path: выпуск токена и обращение к API |

---

## 4. Пробелы в покрытии

!!! warning "Перечисленное ниже не проверяется ни одним тестом"
    **Безопасность (наибольший риск):**

    1. `ApiTokenAuthenticationFilter` — нет тестов вообще: ответы 401/429, пропуск запроса
       без Bearer-заголовка, установка `SecurityContext`, записи аудита.
    2. `ApiTokenAuthenticationProvider` — `BadCredentialsException`, неверный формат, реthrow лимита.
    3. `ApiTokenAuthentication` — principal/credentials, фабрики, набор authority.
    4. `ApiTokenPermissionEvaluator` — оба `hasPermission`, `hasAnyAuthority`, анонимный случай.
    5. `InternalTokenOwnerResolver` — все ветки (владелец из токена, `UserDetails`, `String`, отсутствие аутентификации).
    6. Готовая цепочка `openapiTokensSecurityFilterChain` — не загружается ни в одном тесте:
       ни `hasRole("ADMIN")`, ни отключение CSRF, ни порядок фильтров.
    7. Нет ни одного HTTP-теста на 401 при неверном токене, 403 для не-админа и 429 на уровне HTTP.

    **Web:**

    8. `ApiTokenExceptionHandler` — все четыре маппинга (404/409/400/400) не проверяются,
       ни один тест не проверяет тело `ProblemDetail`.
    9. `AdminApiTokenController` — не покрыты `GET /`, `GET /{id}`, `GET /{id}/audit`, `DELETE`, `unblock`.
    10. `UserApiTokenController` — не покрыты `GET /{id}`, `GET /{id}/audit`, ошибки валидации,
        заголовок `Location`, проброс `tenantId`/`slidingTtlSeconds`/лимитов.
    11. `ApiTokenWebMapper` — нет юнит-тестов преобразований.

    **Конфигурация:**

    12. `OpenApiTokensPersistenceAutoConfiguration` — условия `@ConditionalOnBean(DataSource)`,
        `@ConditionalOnClass(EntityManager)` не проверяются.
    13. Валидация `OpenApiTokensProperties` (`@Min`, `@NotBlank`) — не проверяется.
    14. Не покрыты ветки: `auth.mode=keycloak`, `scopes.mode=mapped`, `hashing.algorithm=bcrypt`
        через контекст, кастомные `token.header`/`bearer-prefix`, переопределение бинов.
    15. `TransactionalApiTokenService` — границы транзакций не проверяются.

    **Прочее:**

    16. Модуль `spring-boot-starter` — тестов нет; файл `AutoConfiguration.imports` не проверяется.
    17. `openapi-tokens-web` содержит неиспользуемые зависимости Cucumber (`cucumber-java`,
        `cucumber-junit-platform-engine`, `cucumber-spring`) без feature-файлов.
    18. Нет измерения покрытия — нет и «порога», ниже которого сборка падает.
    19. Мультитенантность проверяется только на уровне claim: фильтрация `scope_mapping`
        по тенанту — на одном тенанте, фильтрации токенов по тенанту нет вовсе.
    20. Нет проверок энтропии токена, коллизий префикса и стойкости параметров хеширования.
    21. `ApiTokenRepository#findAll`, пагинация аудита, `usageStats` по отказам,
        `deleteByScope` для отсутствующего скоупа — не покрыты.

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
| 🔴 Высокий | Тесты фильтра аутентификации (401/429, пропуск, аудит) | Это горячий путь безопасности, полностью непокрытый |
| 🔴 Высокий | HTTP-тесты готовой цепочки безопасности (401/403/429) | Проверить реальные правила доступа |
| 🔴 Высокий | Тесты `ApiTokenExceptionHandler` (тела `ProblemDetail`) | Зафиксировать контракт ошибок |
| 🟠 Средний | Валидация свойств и ветки автоконфигурации (`keycloak`, `mapped`, `bcrypt`) | Защита от регрессий конфигурации |
| 🟠 Средний | BDD для REST- и UI-потоков | Требование правил проекта; сейчас только 3 feature-файла |
| 🟡 Низкий | JaCoCo + порог покрытия | Измеримость |
| 🟡 Низкий | Тесты мультитенантности (фильтрация по `tenant_id`) | Требуется при мультитенантном продукте |
| 🟡 Низкий | Удалить неиспользуемые Cucumber-зависимости из `web` или добавить там feature-файлы | Чистота сборки |

---

## 7. Тестирование в CI

Для CI-пайплайна достаточно:

```yaml
# пример (GitLab CI)
test:
  image: eclipse-temurin:21-jdk
  services:
    - docker:dind            # Testcontainers требует Docker
  variables:
    TESTCONTAINERS_RYUK_DISABLED: "true"
  script:
    - ./gradlew test --no-daemon
  artifacts:
    when: always
    reports:
      junit: "*/build/test-results/test/*.xml"
```

Учтите: `TESTCONTAINERS_RYUK_DISABLED=true` отключает «сборщик мусора» контейнеров,
поэтому упавший прогон может оставить контейнеры — при необходимости периодически чистите их.

## Связанные разделы

- [Ограничения и roadmap](roadmap.md) — что планируется исправить.
- [Публикация документации](publishing.md) — отдельный CI для сайта.
- [Бэкенд-разработчику](../guides/backend.md) — примеры интеграционных тестов.
