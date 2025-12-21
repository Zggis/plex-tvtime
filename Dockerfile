# Build stage
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle build -x test --no-daemon

# Runtime stage
FROM zenika/alpine-chrome:with-selenoid
EXPOSE 8080
USER root
RUN apk add dpkg
RUN apk add openjdk17
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-XX:+UseSerialGC","-Xss512k", "-Xms64m", "-Xmx128m", "-jar","-Dspring.profiles.active=docker","./app.jar"]