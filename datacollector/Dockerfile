# syntax=docker/dockerfile:1

FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.authors="scarlxrd" \
      org.opencontainers.image.source="https://github.com/Sc4rlxrd/Dashboard-Java-e-Python"

RUN apk add --no-cache \
    chromium \
    chromium-chromedriver \
    ttf-freefont

WORKDIR /app

COPY --from=build \
    /workspace/target/*.jar \
    /app/app.jar

ENV CHROME_BIN=/usr/bin/chromium-browser \
    CHROMEDRIVER_PATH=/usr/bin/chromedriver

ENTRYPOINT ["java", "-jar", "/app/app.jar"]