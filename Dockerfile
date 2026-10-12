FROM eclipse-temurin:25 AS build
WORKDIR /app

# Install Gradle 9.1.0 directly — avoids gradlew CRLF/permission issues on upload
RUN apt-get update -q && apt-get install -y -q wget unzip \
 && wget -q https://services.gradle.org/distributions/gradle-9.1.0-bin.zip -O /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && rm /tmp/gradle.zip
ENV PATH="/opt/gradle-9.1.0/bin:$PATH"

COPY . .
RUN gradle :backend:bootJar --no-daemon -q

FROM eclipse-temurin:25
WORKDIR /app
COPY --from=build /app/backend/build/libs/backend-*.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
