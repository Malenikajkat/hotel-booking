# Hotel Booking Microservices

Распределённая система бронирования отелей на базе Spring Boot с микросервисной архитектурой.

## Архитектура системы

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────────┐     ┌──────────────────┐
│   Client    │────▶│  API Gateway     │────▶│  Hotel Management   │────▶│   H2 Database    │
│             │     │  (Port 8080)     │     │  Service (8081)     │     │                  │
└─────────────┘     └──────────────────┘     └─────────────────────┘     └──────────────────┘
                            │                          ▲
                            │                          │
                            ▼                          │
                    ┌──────────────────┐     ┌─────────────────────┐
                    │   Eureka Server  │◀───▶│    Booking Service  │────▶│   H2 Database    │
                    │   (Port 8761)    │     │    (Port 8082)      │     │                  │
                    └──────────────────┘     └─────────────────────┘     └──────────────────┘

Компоненты:
├── eureka-server          — Service Discovery (порт 8761)
├── api-gateway            — API Gateway + JWT validation (порт 8080)
├── hotel-booking-common   — Общие компоненты (ErrorDto, GlobalExceptionHandler, TraceIdFilter)
├── hotel-management-service — Управление отелями и номерами (порт 8081)
└── booking-service        — Управление бронированиями (порт 8082)
```

### Компоненты

| Компонент | Порт | Описание |
|-----------|------|----------|
| Eureka Server | 8761 | Сервис обнаружения сервисов (Service Discovery) |
| API Gateway | 8080 | Шлюз маршрутизации с JWT-валидацией |
| Hotel Management Service | 8081 | Управление отелями и номерами |
| Booking Service | 8082 | Создание и управление бронированиями |

## Технологии

- **Java 17+**
- **Spring Boot 3.4.1**
- **Spring Cloud 2024.0.0** (Gateway, Eureka, LoadBalancer)
- **Spring Data JPA + H2** (in-memory базы данных)
- **Spring Security + JWT** (аутентификация)
- **Lombok, MapStruct**
- **SpringDoc OpenAPI** (Swagger UI)
- **Resilience4j** (Circuit Breaker, Retry)
- **MDC** (correlation tracing)

## Запуск проекта

### Требования

- Java 17+
- Maven 3.9+

### Шаги запуска

1. **Склонируйте репозиторий:**
   ```bash
   git clone <repository-url>
   cd hotel-booking
   ```

2. **Соберите проект:**
   ```bash
   mvn clean package -DskipTests
   ```

3. **Запустите сервисы в следующем порядке:**
   ```bash
   # 1. Eureka Server
   java -jar eureka-server/target/eureka-server-1.0-SNAPSHOT.jar
   
   # 2. API Gateway (в новом терминале)
   java -jar api-gateway/target/api-gateway-1.0-SNAPSHOT.jar
   
   # 3. Hotel Management Service (в новом терминале)
   java -jar hotel-management-service/target/hotel-management-service-1.0-SNAPSHOT.jar
   
   # 4. Booking Service (в новом терминале)
   java -jar booking-service/target/booking-service-1.0-SNAPSHOT.jar
   ```

4. **Откройте Swagger UI:**
   - Hotel Management Service: http://localhost:8081/swagger-ui.html
   - Booking Service: http://localhost:8082/swagger-ui.html
   - Eureka Server: http://localhost:8761

## Предзаполненные данные

### Пользователи (Booking Service)
| Логин | Пароль | Роль |
|-------|--------|------|
| admin | admin123 | ADMIN |
| user | user123 | USER |

### Отели (Hotel Management Service)
| Отель | Адрес |
|-------|-------|
| Grand Hotel Moscow | Red Square, Moscow |
| Seaside Resort | Nevsky Prospect, St. Petersburg |

### Номера
- Grand Hotel Moscow: номера 101, 102, 201
- Seaside Resort: номера 301, 302

## API Endpoints

### API Gateway (http://localhost:8080)

Маршрутизирует запросы к соответствующим сервисам:
- `/api/hotels/**` → Hotel Management Service
- `/api/rooms/**` → Hotel Management Service
- `/api/bookings/**` → Booking Service
- `/api/booking/**` → Booking Service
- `/api/user/**` → Booking Service

### Hotel Management Service

| Метод | Endpoint | Описание | Роли |
|-------|----------|----------|------|
| POST | `/api/hotels` | Создать отель | ADMIN |
| GET | `/api/hotels` | Получить список отелей | USER, ADMIN |
| GET | `/api/hotels/{id}` | Получить отель по ID | USER, ADMIN |
| PATCH | `/api/hotels/{id}` | Обновить отель | ADMIN |
| DELETE | `/api/hotels/{id}` | Удалить отель | ADMIN |
| POST | `/api/rooms` | Создать номер | ADMIN |
| GET | `/api/rooms` | Получить свободные номера | USER, ADMIN |
| GET | `/api/rooms/recommend` | Рекомендованные номера (по times_booked) | USER, ADMIN |
| GET | `/api/rooms/{id}` | Получить номер по ID | USER, ADMIN |
| POST | `/api/rooms/{id}/confirm-availability` | Подтвердить доступность | INTERNAL |
| POST | `/api/rooms/{id}/release` | Снять блокировку | INTERNAL |
| PATCH | `/api/rooms/{id}/times-booked` | Увеличить счётчик бронирований | ADMIN |

### Booking Service

| Метод | Endpoint | Описание | Роли |
|-------|----------|----------|------|
| POST | `/api/user/register` | Регистрация пользователя | PUBLIC |
| POST | `/api/user/auth` | Авторизация (получение JWT) | PUBLIC |
| POST | `/api/booking` | Создать бронирование | USER |
| GET | `/api/bookings` | История бронирований | USER |
| GET | `/api/booking/{id}` | Получить бронирование | USER |
| DELETE | `/api/booking/{id}` | Отменить бронирование | USER |
| GET | `/api/user` | Список пользователей | ADMIN |
| PATCH | `/api/user/{id}` | Обновить пользователя | ADMIN |
| DELETE | `/api/user/{id}` | Удалить пользователя | ADMIN |
| POST | `/api/user` | Создать пользователя | ADMIN |

## Алгоритм бронирования (Saga Pattern)

Процесс создания бронирования:

1. **Booking Service** создаёт бронирование в статусе `PENDING`
2. **Booking Service** вызывает **Hotel Management Service** для подтверждения доступности номера
3. При успешном подтверждении: статус меняется на `CONFIRMED`, увеличивается счётчик `times_booked`
4. При ошибке: выполняется компенсация — бронирование отменяется (`CANCELLED`), блокировка снимается

### Подтверждение доступности номера
- Номер проверяется на наличие пересекающихся бронирований
- При успешной проверке создаётся временная блокировка слота
- Используется корреляционный ID для идемпотентности
- **Resilience4j Retry** с exponential backoff (3 попытки, 1s/2s/4s)
- **Pessimistic Locking** (`@Lock(PESSIMISTIC_WRITE)`) для предотвращения race conditions

### Автоматический подбор номера
При `autoSelect: true` система предлагает номера, отсортированные по возрастанию `times_booked` (равномерная загрузка).

### Обработка параллельных запросов
- **Pessimistic Locking** на уровне БД предотвращает создание дубликатов при конкурентных запросах
- **Unique Constraint** на `(user_id, room_id, start_date, end_date)` обеспечивает целостность
- **409 Conflict** возвращается при обнаружении пересечения бронирований

## Безопасность

- **JWT-аутентификация** (срок действия токена: 1 час)
- **Пароли хранятся в закодированном виде** (BCrypt)
- **Роли:** USER (личные операции), ADMIN (управление)
- Каждый сервис самостоятельно валидирует JWT-токен (Resource Server)
- Gateway маршрутизирует запросы и передаёт информацию о пользователе через заголовки
- **@PreAuthorize** для разграничения доступа на уровне методов

## Тестирование

### Запуск всех тестов:
```bash
mvn test
```

### Запуск тестов для конкретного модуля:
```bash
mvn test -pl hotel-management-service
mvn test -pl booking-service
mvn test -pl api-gateway
```

### Примеры тестовых сценариев:
- CRUD операции для отелей и номеров
- CRUD операции для бронирований
- Сортировка номеров по `times_booked`
- Подтверждение и отмена доступности номеров
- Идемпотентность подтверждений (correlationId, requestId)
- Регистрация и авторизация пользователей
- Валидация дат бронирования
- Ownership check при отмене бронирования
- Service-layer тесты с Mockito
- Тесты Saga Pattern (подтверждение, компенсация)
- Тесты параллельных бронирований (pessimistic locking)
- Тесты timeout и retry (Resilience4j)
- Тесты ошибок авторизации (401/403)

## Структура базы данных

### Booking Service (H2)
- **users**: id, username (unique), password (BCrypt), role
- **bookings**: id, user_id, room_id, start_date, end_date, status (PENDING/CONFIRMED/CANCELLED), correlation_id, created_at
  - Unique constraint: (user_id, room_id, start_date, end_date)

### Hotel Management Service (H2)
- **hotels**: id, name, address
- **rooms**: id, hotel_id, room_number, available (BOOLEAN), times_booked
- **bookings**: id, room_id, start_date, end_date, correlation_id (временные слоты)

## Логирование и трассировка

- **MDC TraceId**: каждый запрос получает уникальный traceId (заголовок `X-Trace-Id` или генерируется автоматически)
- **Корреляционные ID**: каждое бронирование имеет correlationId для сквозной трассировки
- **Структурированное логирование**: ключевые события логируются с traceId и correlationId
- **Pattern**: `%d{yyyy-MM-dd HH:mm:ss} [%thread] [%X{traceId:-}] %-5level %logger{36} - %msg%n`

## ADR (Architecture Decision Records)

### ADR-001: Выбор микросервисной архитектуры
**Статус**: Принято  
**Контекст**: Система должна поддерживать независимое масштабирование сервисов бронирования и управления отелями.  
**Решение**: Использовать микросервисную архитектуру с Spring Cloud.  
**Последствия**: Увеличена сложность развертывания, но обеспечена независимость сервисов.

### ADR-002: Двухфазная согласованность (Saga Pattern)
**Статус**: Принято  
**Контекст**: Глобальные распределённые транзакции (2PC/XA) усложняют архитектуру и снижают производительность.  
**Решение**: Использовать Saga Pattern с компенсацией. Booking Service создаёт бронирование в PENDING, затем запрашивает подтверждение у Hotel Service. При неудаче — компенсирует отмену.  
**Последствия**: Eventual consistency, требуется обработка временных блокировок.

### ADR-003: JWT-аутентификация в каждом сервисе
**Статус**: Принято  
**Контекст**: Gateway не должен быть единой точкой авторизации.  
**Решение**: Каждый сервис (Hotel Management, Booking) самостоятельно валидирует JWT-токен через JwtAuthenticationFilter.  
**Последствия**: Дублирование логики валид��ции, но повышенная безопасность и независимость сервисов.

### ADR-004: In-memory база данных (H2)
**Статус**: Принято  
**Контекст**: Для разработки и тестирования достаточно in-memory базы.  
**Решение**: Использовать H2 в режиме in-memory с JPA auto-DDL.  
**Последствия**: Данные теряются при перезапуске, но упрощена настройка окружения.

### ADR-005: BCrypt для хеширования паролей
**Статус**: Принято  
**Контекст**: Пароли не должны храниться в открытом виде.  
**Решение**: Использовать BCryptPasswordEncoder для хеширования всех паролей.  
**Последствия**: Все сервисы используют единый PasswordEncoder bean.

### ADR-006: MDC для сквозной трассировки
**Статус**: Принято  
**Контекст**: В распределённой системе сложно отследить запрос через все сервисы.  
**Решение**: Использовать SLF4J MDC с TraceIdFilter, который извлекает или генерирует traceId и добавляет его в MDC.  
**Последствия**: Логи содержат traceId, что упрощает отладку.

### ADR-007: Resilience4j для отказоустойчивости
**Статус**: Принято  
**Контекст**: Hotel Service может быть недоступен или отвечать с задержкой.  
**Решение**: Использовать Resilience4j @Retryable (3 попытки, exponential backoff) и @CircuitBreaker для вызовов между сервисами.  
**Последствия**: Система устойчива к временным сбоям Hotel Service, автоматический fallback при превышении числа попыток.

### ADR-008: Pessimistic Locking для предотвращения race conditions
**Статус**: Принято  
**Контекст**: При конкурентных запросах на один и тот же номер возможны дубликаты бронирований.  
**Решение**: Использовать `@Lock(PESSIMISTIC_WRITE)` в JPA репозиториях для блокировки строк при проверке доступности.  
**Последствия**: Гарантируется, что два пользователя не могут забронировать один номер на пересекающиеся даты. Небольшое снижение производительности при высокой нагрузке компенсируется корректностью данных.

### ADR-009: Общий модуль hotel-booking-common
**Статус**: Принято  
**Контекст**: ErrorDto, GlobalExceptionHandler и TraceIdFilter дублируются в каждом сервисе.  
**Решение**: Вынести общие компоненты в отдельный Maven модуль `hotel-booking-common`.  
**Последствия**: Устранено дублирование кода, единая точка изменения для общих компонентов.

## Качество кода

- **SOLID**: каждый сервис имеет чёткое разделение ответственности (Controller → Service → Repository)
- **DRY/KISS**: общие паттерны (GlobalExceptionHandler, ErrorDto, JWT-фильтры) вынесены в отдельные компоненты
- **Google Java Format**: стиль кодирования enforced через maven-compiler-plugin
- **Checkstyle**: валидация кода через maven-checkstyle-plugin

## Авторы

Проект разработан в рамках обучения Spring Boot и микросервисной архитектуре.
