# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the root pom and common module pom to cache dependencies
COPY pom.xml .
COPY common/pom.xml common/
COPY discovery-service/pom.xml discovery-service/

# Install common module and download discovery-service dependencies
RUN mvn install -pl common -am -DskipTests

# Copy source code and build the service
COPY common/src common/src
COPY discovery-service/src discovery-service/src
RUN mvn package -pl discovery-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/discovery-service/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
