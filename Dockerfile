FROM eclipse-temurin:25 AS build
WORKDIR /app
COPY . .
RUN ./gradlew :backend:bootJar --no-daemon -q

FROM eclipse-temurin:25
WORKDIR /app
COPY --from=build /app/backend/build/libs/backend-*.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
