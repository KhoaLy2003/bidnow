# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY api-gateway/pom.xml api-gateway/

RUN mvn install -pl common -am -DskipTests

COPY common/src common/src
COPY api-gateway/src api-gateway/src
RUN mvn package -pl api-gateway -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
