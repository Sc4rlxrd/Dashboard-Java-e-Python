FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
LABEL authors="scalxrd"
COPY . .
RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine


RUN apk add --no-cache chromium chromium-chromedriver ttf-freefont
COPY --from=build /app/target/*.jar app.jar


ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROME_PATH=/usr/lib/chromium/

ENTRYPOINT ["java", "-jar", "app.jar"]