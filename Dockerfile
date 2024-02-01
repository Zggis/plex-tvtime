FROM zenika/alpine-chrome:with-chromedriver
EXPOSE 8080
USER root
RUN apk add dpkg
RUN apk add openjdk17
COPY /build/libs/*.jar app.jar
ENTRYPOINT ["java","-XX:+UseSerialGC","-Xss512k", "-Xms64m", "-Xmx128m", "-jar","-Dspring.profiles.active=docker","./app.jar"]