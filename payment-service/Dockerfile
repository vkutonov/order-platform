# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY --chmod=755 gradlew ./gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY order-service/build.gradle order-service/
COPY inventory-service/build.gradle inventory-service/
COPY payment-service/build.gradle payment-service/
COPY payment-service/src/main payment-service/src/main

RUN --mount=type=cache,id=payment-service-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew :payment-service:bootJar --no-daemon

RUN cp payment-service/build/libs/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system app \
    && useradd \
    --system \
    --gid app \
    --no-create-home \
    --shell /usr/sbin/nologin \
    app

COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

USER app

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
