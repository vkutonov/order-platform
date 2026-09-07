# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle

COPY settings.gradle build.gradle ./

COPY order-service/build.gradle order-service/
COPY inventory-service/build.gradle inventory-service/

COPY inventory-service/src inventory-service/src

# BuildKit cache mount для использования кэша между builds
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :inventory-service:bootJar --no-daemon


FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# создаем non-root пользователя
RUN groupadd --system app \
    && useradd \
    --system \
    --gid app\
    --no-create-home \
    --shell /usr/sbin/nologin \
    app

COPY --from=builder --chmod=0444 \
    /workspace/inventory-service/build/libs/*.jar \
    app.jar


USER app

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]