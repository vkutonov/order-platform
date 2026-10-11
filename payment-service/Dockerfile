# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY --chmod=755 gradlew ./gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY order-service/build.gradle order-service/
COPY inventory-service/build.gradle inventory-service/
COPY payment-service/build.gradle payment-service/
COPY payment-service/src payment-service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :payment-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system app \
    && useradd \
    --system \
    --gid app \
    --no-create-home \
    --shell /usr/sbin/nologin \
    app

COPY --from=builder --chmod=0444 \
    /workspace/payment-service/build/libs/*.jar \
    app.jar

USER app

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
