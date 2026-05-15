# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY bidding-service/pom.xml bidding-service/

RUN mvn install -pl common -am -DskipTests

COPY common/src common/src
COPY bidding-service/src bidding-service/src
RUN mvn package -pl bidding-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/bidding-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
