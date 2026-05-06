# syntax=docker/dockerfile:1.7

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle wrapper and config first to leverage layer caching
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
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root
RUN useradd --system --uid 1001 --create-home spring
USER spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]