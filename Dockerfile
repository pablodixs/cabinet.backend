# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup --system cabinet \
    && adduser --system --ingroup cabinet cabinet

WORKDIR /app

COPY --from=build --chown=cabinet:cabinet /workspace/app.jar ./app.jar

USER cabinet

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
