# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY media-service/pom.xml media-service/
COPY common/src common/src

RUN mvn install -pl common -am -DskipTests

COPY media-service/src media-service/src
RUN mvn package -pl media-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/media-service/target/*.jar app.jar
USER appuser
EXPOSE 8086
ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
