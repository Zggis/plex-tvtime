FROM registry.access.redhat.com/ubi9/openjdk-17:1.17-1.1705573248
USER root
RUN microdnf -y update --nodocs
RUN microdnf -y install wget
RUN microdnf -y install unzip
RUN microdnf -y install libatk-1.0.so.0
RUN wget https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/121.0.6167.85/linux64/chromedriver-linux64.zip
RUN wget https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/121.0.6167.85/linux64/chrome-headless-shell-linux64.zip
RUN wget https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/121.0.6167.85/linux64/chrome-linux64.zip
RUN unzip chromedriver-linux64.zip
RUN unzip chrome-headless-shell-linux64.zip
RUN unzip chrome-linux64.zip
RUN rm chromedriver-linux64.zip
RUN rm chrome-headless-shell-linux64.zip
RUN rm chrome-linux64.zip
EXPOSE 8080
COPY /build/libs/*.jar app.jar
RUN chmod 777 /home
RUN chmod -R 777 /home/default
RUN useradd plextvtime
USER plextvtime
ENTRYPOINT ["java","-XX:+UseSerialGC","-Xss512k", "-Xms64m", "-Xmx128m", "-jar","-Dspring.profiles.active=docker","./app.jar"]