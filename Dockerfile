FROM amazoncorretto:17.0.6-al2023-RC-headless
EXPOSE 8080
COPY /build/libs/*.jar app.jar
ENTRYPOINT ["java","-XX:+UseSerialGC","-Xss512k", "-Xms64m", "-Xmx128m", "-jar","-Dspring.profiles.active=docker","/app.jar"]