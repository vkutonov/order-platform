# Payment service

На этапе 1 подключены Gradle, PostgreSQL, Flyway, JPA (`ddl-auto: validate`),
Actuator, Docker Compose и CI. Платёжные сценарии и Kafka consumers появятся
на следующих этапах.

## Сборка и запуск

Из корня репозитория:

```powershell
.\gradlew.bat :payment-service:build

# Отдельная сборка модуля, аналогично CI:
.\payment-service\gradlew.bat -p payment-service build

docker compose up -d --build --wait payment-service
Invoke-RestMethod http://localhost:8083/actuator/health
```

Тесты используют PostgreSQL 18.4 через Testcontainers и проверяют соединение
с собственной БД, инициализацию Flyway и endpoint здоровья. Для них нужен Docker.

В Compose сервис использует `payment-postgres`, собственную базу `payment_db`
и постоянный volume `payment-postgres-data`. По умолчанию HTTP доступен на
порту 8083, PostgreSQL — на 5435. Переменные `PAYMENT_SERVICE_PORT` и
`PAYMENT_DB_*` перечислены в корневом `.env.example`; приложение также читает
корневой `.env`. Значения по умолчанию совпадают с примером.

Flyway создаёт свою таблицу истории. Бизнес-таблицы и SQL-миграции в
`src/main/resources/db/migration` будут добавлены на этапе 2.
