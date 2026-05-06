# syntax=docker/dockerfile:1.7

# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Alpine ships with /bin/sh (ash), not bash — gradlew works fine with sh
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
# If you have a gradle.properties, uncomment:
# COPY gradle.properties ./

RUN chmod +x gradlew && ./gradlew --version

# Pre-fetch dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon || true

# Now copy sources and build
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Alpine uses adduser, not useradd
RUN addgroup -S spring && adduser -S -G spring -u 1001 spring
USER spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]