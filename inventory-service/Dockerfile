# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY --chmod=755 gradlew ./gradlew
COPY gradle gradle

COPY settings.gradle build.gradle ./

COPY inventory-grpc-contract/build.gradle inventory-grpc-contract/
COPY order-service/build.gradle order-service/
COPY inventory-service/build.gradle inventory-service/
COPY payment-service/build.gradle payment-service/

COPY inventory-grpc-contract/src/main inventory-grpc-contract/src/main
COPY inventory-service/src/main inventory-service/src/main

# BuildKit cache mount для использования кэша между builds
RUN --mount=type=cache,id=inventory-service-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew :inventory-service:bootJar --no-daemon

RUN cp inventory-service/build/libs/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted


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

COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

USER app

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
