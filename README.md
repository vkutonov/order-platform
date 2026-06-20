# Order Platform

Event-driven microservice-based order platform for portfolio and learning purposes.

## Services

- `order-service` — owns order lifecycle and immutable order item snapshots.
- `catalog-service` — will own product data and prices.
- `inventory-service` — will own stock and reservations.
- `payment-service` — will own payments.
- `notification-service` — will own notification delivery history.

## Current stage

Initial setup of `order-service`.

## Tech stack

- Java 21
- Spring Boot
- Gradle
- PostgreSQL
- Flyway
- Docker Compose

## Run infrastructure

```bash
docker compose up -d
```

## Run infrastructure

```bash
docker compose up -d
Run order-service
cd order-service
./gradlew bootRun
```

On Windows PowerShell:
```bash
cd order-service
.\gradlew.bat bootRun
```
---
