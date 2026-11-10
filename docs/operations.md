# Запуск, API и эксплуатация

[К README](../README.md) · [Архитектура](architecture.md) · [Сценарии заказа](demo.md)

## Запуск и окружение

Основной способ запуска описан в [README](../README.md): `docker compose up -d --build --wait`.
Для контейнерного запуска локальная установка JDK не нужна.

Compose читает `.env` из корня проекта. Шаблон — [`.env.example`](../.env.example).
Приложения при запуске из IDE также читают `.env` из текущего или родительского каталога.

| Компонент | Порт на хосте из шаблона |
|---|---|
| Order / Inventory / Payment | `8081` / `8082` / `8083` |
| Inventory gRPC | `9090` (`INVENTORY_GRPC_HOST_PORT`) |
| PostgreSQL Order / Inventory / Payment | `5433` / `5434` / `5435` |
| Kafka для приложений на хосте | `9092` |
| Kafka UI | `8085` |

В Docker сервисы обращаются к Kafka по `kafka:19092`, к своим БД — по имени сервиса
на порту `5432`. Из IDE используются внешние порты из таблицы.
Профили для ручного подтверждения оплаты подключать не нужно.

Snapshots передаются через gRPC, бизнес-события — через Kafka. Из IDE order использует
`INVENTORY_GRPC_TARGET=static://localhost:9090` по умолчанию; в Compose адрес задан как
`static://inventory-service:9090`. Если внешний порт изменён через `INVENTORY_GRPC_HOST_PORT`,
укажите его в `INVENTORY_GRPC_TARGET` для order, запущенного из IDE.
`INVENTORY_GRPC_PORT` меняет порт сервера при запуске inventory из IDE (по умолчанию `9090`).

Полезные команды из корня:

```powershell
docker compose config --quiet
docker compose ps
docker compose logs -f payment-service
docker compose down
```

После изменения Java-кода нужна сборка образа и обновление контейнера:

```powershell
docker compose up -d --build --wait order-service inventory-service payment-service
```

`docker compose restart` перезапускает существующий контейнер с прежним образом.
Пересборка образа отдельно от `up` также не обновляет уже работающий контейнер.

`POSTGRES_DB`, `POSTGRES_USER` и `POSTGRES_PASSWORD` применяются при инициализации
пустого тома. Изменение `.env` само по себе не меняет пароль или имя существующей БД.
Существующие учётные данные меняются в PostgreSQL.
`docker compose down` сохраняет тома, а `docker compose down -v` удаляет локальные данные БД.

### Запуск из IDE

Нужен JDK 21. Поднимите инфраструктуру:

```powershell
docker compose up -d order-postgres inventory-postgres payment-postgres kafka kafka-ui kafka-init
```

Дождитесь состояния `healthy` у БД и Kafka; `kafka-init` должен завершиться с кодом `0`.
Состояние всех контейнеров видно в `docker compose ps -a`.

Запускайте приложения из корня проекта в отдельных терминалах:

```powershell
.\gradlew.bat :inventory-service:bootRun
```

```powershell
.\gradlew.bat :order-service:bootRun
```

```powershell
.\gradlew.bat :payment-service:bootRun
```

Для Linux/macOS используйте `./gradlew` вместо `.\gradlew.bat`.
Если сервисы уже запущены в Docker на тех же портах, сначала остановите их контейнеры
или задайте другие порты.

## API

Полный состав запросов и схемы ответов доступны в Swagger каждого сервиса.

| Сервис | Метод и путь | Назначение |
|---|---|---|
| Order, `8081` | `POST /api/orders` | Создать заказ |
| Order | `GET /api/orders/{id}` | Получить заказ |
| Order | `GET /api/orders/user/{userId}` | Получить заказы пользователя |
| Order | `GET /api/orders/{id}/history` | Получить историю статусов |
| Order | `POST /api/orders/{id}/cancel` | Отменить заказ до оплаты |
| Inventory, `8082` | `POST /api/products` | Создать товар и начальный остаток |
| Inventory | `GET /api/products` | Получить товары с остатками, кроме `DELETED` |
| Inventory | `DELETE /api/products/{id}` | Мягко удалить товар |
| Inventory | `GET /api/products/{id}/stock` | Получить общий, зарезервированный и доступный остаток |
| Inventory | `POST /api/products/{id}/stock` | Пополнить остаток |
| Payment, `8083` | `GET /api/payments` | Получить платежи, сначала новые |
| Payment | `POST /api/payments/by-order/{orderId}/confirm` | Подтвердить платёж заказа |
| Payment | `POST /api/payments/by-order/{orderId}/reject` | Отклонить платёж заказа |


### Удаление товара

Удаление выставляет `DELETED` и возвращает `204`. Повторное удаление также возвращает `204`,
неизвестный UUID — `404`. Удалённый товар скрыт из списка; публичные просмотр остатка
и пополнение возвращают `404`. Существующие резервы сохраняются и могут завершиться.
Новые резервирования такого товара отклоняются.

### Подтверждение, отказ и отмена

Ручки платежа принимают `orderId`, тело запроса не требуется.
`paymentId` генерируется отдельно и возвращается в ответах и событиях.

Если платёж ещё не создан, ответ — `404 / PAYMENT_NOT_FOUND`.
Для завершённой попытки повторный запрос возвращает `200` с сохранённым результатом.
Так, `confirm` после отказа вернёт `FAILED`, а `reject` после успеха — `SUCCEEDED`.
Если ожидающий платёж уже просрочен, запрос завершает его с `SESSION_EXPIRED`.

Отказ или истечение срока приводит к отмене заказа через Kafka.
Повторная ручная отмена заказа возвращает `204`; отмена уже `PAID` запрещена.

### Ошибки REST API

Во всех сервисах используются поля `timestamp`, `status`, `code`, `message` и `path`.
При валидации добавляется `fieldErrors` с парами `field` / `message`;
для отсутствующих товаров Order и Inventory могут вернуть `missingProductIds`.
Пустые дополнительные списки опускаются.

При получении snapshots Order возвращает `404 / PRODUCT_NOT_FOUND` с точными
`missingProductIds`, `503 / INVENTORY_UNAVAILABLE` при недоступности inventory,
`504 / INVENTORY_TIMEOUT` при истечении deadline (3 секунды) и
`502 / INVENTORY_ERROR` при других ошибках зависимости. Внутренние сообщения gRPC
не передаются в REST-ответ. Заказ при этих ошибках не сохраняется.

Пример ответа payment-service:

```json
{
  "timestamp": "2026-11-02T10:00:00Z",
  "status": 404,
  "code": "PAYMENT_NOT_FOUND",
  "message": "Payment not found",
  "path": "/api/payments/by-order/00000000-0000-0000-0000-000000000001/confirm"
}
```

Клиент может ориентироваться на `status` и стабильный `code`.
Для внутренних ошибок возвращается `INTERNAL_SERVER_ERROR` с общим сообщением;
подробности остаются в логах.

## Kafka и DLT

Основные топики — `order.events`, `inventory.events`, `payment.events`.
Они и DLT создаются контейнером `kafka-init`. В Compose у каждого топика три partition
и replication factor 1: это локальная конфигурация с одним брокером.

| Входящий поток / потребитель | DLT |
|---|---|
| События заказа → Inventory | `order.events.inventory-service.dlt` |
| События заказа → Payment | `order.events.payment-service.dlt` |
| События склада → Order | `inventory.events.order-service.dlt` |
| События оплаты → Order | `payment.events.order-service.dlt` |

Посмотреть топики:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list
```

В Kafka UI откройте нужный топик и найдите сообщение по `orderId` в ключе и payload.
Для DLT проверьте исходное сообщение, заголовки ошибки и логи потребителя.
После исправления причины сообщение можно повторно опубликовать в исходный топик,
сохранив ключ и исходный `eventId`. Временные ошибки уже откатывают inbox вместе
с бизнес-изменениями; новый `eventId` для обхода проверки повторов не нужен.

Автоматического replay и очистки DLT сейчас нет.
Записи отмены в inbox участвуют в защите от позднего создания: их нельзя удалять
без замены этого признака постоянным состоянием.

## Тесты и CI

Из корня проекта, при наличии JDK 21 и запущенного Docker:

```powershell
.\gradlew.bat build
```

Для одного сервиса:

```powershell
.\gradlew.bat :payment-service:test
```

Unit-тесты проверяют доменные правила и сервисную логику, MockMvc — REST-контракты,
Testcontainers — PostgreSQL и Flyway. Сценарии Kafka используют встроенный broker.

В [GitHub Actions](../.github/workflows/ci.yml) отдельный matrix job собирает и тестирует
каждый сервис. Docker job проверяет Compose, собирает образы и ждёт health checks.
Проверка `UP` не заменяет сквозной сценарий покупки; его шаги описаны в [сценариях заказа](demo.md).

Образы собираются в несколько стадий, runtime запускается от непривилегированного
пользователя. Слои зависимостей отделены от приложения. Gradle-кэши сборки разделены
по сервисам и используют `sharing=locked`; CI сохраняет их содержимое между запусками.
