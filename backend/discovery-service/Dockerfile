# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY discovery-service/pom.xml discovery-service/
COPY common/src common/src

RUN mvn install -pl common -am -DskipTests

COPY discovery-service/src discovery-service/src
RUN mvn package -pl discovery-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/discovery-service/target/*.jar app.jar
USER appuser
EXPOSE 8761
ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
