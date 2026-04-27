# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY wallet-service/pom.xml wallet-service/

RUN mvn install -pl common -am -DskipTests

COPY common/src common/src
COPY wallet-service/src wallet-service/src
RUN mvn package -pl wallet-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/wallet-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
