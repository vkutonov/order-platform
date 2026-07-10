# Order Platform

[![CI](https://github.com/vkutonov/order-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/vkutonov/order-platform/actions/workflows/ci.yml)

Backend-платформа для обработки заказов и управления жизненным циклом заказа.

Сейчас реализован `order-service`: создание заказов, хранение позиций заказа, расчет суммы, переходы между статусами, история изменений, REST API, валидация, PostgreSQL, Flyway и тесты.

## Стек

- Java 21
- Spring Boot 4.1
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Docker Compose
- MapStruct
- Lombok
- OpenAPI / Swagger UI
- JUnit 5, Mockito, MockMvc, Testcontainers
- Gradle

## order-service

`order-service` отвечает за:

- создание заказов;
- хранение снимков позиций заказа;
- расчет итоговой стоимости;
- управление статусами заказа;
- сохранение истории переходов;
- предоставление REST API.

Текущие статусы:

```text
WAITING_FOR_INVENTORY
WAITING_FOR_PAYMENT
PAID
PAYMENT_FAILED
CANCELLED
```

Основные переходы:

| Действие | Из статуса | В статус |
| --- | --- | --- |
| Товар зарезервирован | `WAITING_FOR_INVENTORY` | `WAITING_FOR_PAYMENT` |
| Резервирование не удалось | `WAITING_FOR_INVENTORY` | `CANCELLED` |
| Оплата успешна | `WAITING_FOR_PAYMENT` | `PAID` |
| Оплата не прошла | `WAITING_FOR_PAYMENT` | `PAYMENT_FAILED` |
| Повторная попытка оплаты | `PAYMENT_FAILED` | `WAITING_FOR_PAYMENT` |
| Отмена заказа | `WAITING_FOR_INVENTORY`, `WAITING_FOR_PAYMENT`, `PAYMENT_FAILED` | `CANCELLED` |

Недопустимый переход возвращает `409 Conflict`.

## Запуск

Запустить PostgreSQL:

```bash
docker compose up -d
```

Запустить сервис:

```bash
cd order-service
./gradlew bootRun
```

Для Windows:

```bash
cd order-service
.\gradlew.bat bootRun
```

По умолчанию сервис доступен на порту `8081`.

Swagger UI:

```text
http://localhost:8081/swagger-ui.html
```

## API

Базовый путь:

```text
http://localhost:8081/api/orders
```

| Метод | Путь | Назначение | Успешный ответ |
| --- | --- | --- | --- |
| `POST` | `/api/orders` | Создать заказ | `201 Created` |
| `GET` | `/api/orders/{id}` | Получить заказ по id | `200 OK` |
| `GET` | `/api/orders/user/{userId}` | Получить заказы пользователя | `200 OK` |
| `GET` | `/api/orders/{id}/history` | Получить историю статусов заказа | `200 OK` |
| `POST` | `/api/orders/{id}/reserve` | Подтвердить резервирование товара | `204 No Content` |
| `POST` | `/api/orders/{id}/inventory-failed` | Зафиксировать ошибку резервирования | `204 No Content` |
| `POST` | `/api/orders/{id}/payment-success` | Подтвердить успешную оплату | `204 No Content` |
| `POST` | `/api/orders/{id}/payment-failed` | Зафиксировать ошибку оплаты | `204 No Content` |
| `POST` | `/api/orders/{id}/cancel` | Отменить заказ | `204 No Content` |

Тело запроса для создания заказа:

```json
{
  "userId": "8d2c8c3d-fc8f-4f7a-9a9b-9a1e0a0a0001",
  "items": [
    {
      "productId": "2c6ad9f2-3d7b-4b3a-a93b-6e98c6a00001",
      "productName": "Keyboard",
      "unitPrice": 10.50,
      "quantity": 2
    }
  ]
}
```

Валидация:

| Поле | Правило |
| --- | --- |
| `userId` | обязательное |
| `items` | непустой список |
| `productId` | обязательное |
| `productName` | непустая строка |
| `unitPrice` | обязательное, не меньше `0` |
| `quantity` | обязательное, больше `0` |

## Ошибки API

| HTTP-статус | Код | Причина |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | Ошибка валидации запроса |
| `400` | `INVALID_REQUEST_BODY` | Некорректное тело запроса |
| `400` | `INVALID_PARAMETER_VALUE` | Некорректный path/query параметр |
| `404` | `ORDER_NOT_FOUND` | Заказ не найден |
| `404` | `RESOURCE_NOT_FOUND` | Маршрут не найден |
| `409` | `INVALID_ORDER_STATUS_TRANSITION` | Недопустимый переход статуса |
| `500` | `Internal server error` | Непредвиденная серверная ошибка |

Формат клиентской ошибки:

```json
{
  "timestamp": "2026-07-10T05:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/orders",
  "fieldErrors": []
}
```

## Тесты

Запуск тестов:

```bash
cd order-service
./gradlew test
```

Тесты покрывают доменную логику, сервисный слой, REST-контроллеры, валидацию, JPA-маппинг и Flyway-миграции.

## Дальнейшее развитие

- `Idempotency-Key`;
- Transactional Outbox;
- Kafka-события;
- обработчики событий от inventory/payment;
- `catalog-service`;
- `inventory-service`;
- `payment-service`;
- `notification-service`;
- `api-gateway`;
- JWT-аутентификация.
