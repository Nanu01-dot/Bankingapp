FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/service-registry/pom.xml backend/service-registry/pom.xml
COPY backend/api-gateway/pom.xml backend/api-gateway/pom.xml
COPY backend/customer-service/pom.xml backend/customer-service/pom.xml
COPY backend/account-service/pom.xml backend/account-service/pom.xml
COPY backend/customer-service/src backend/customer-service/src
RUN mvn -f backend/pom.xml -pl customer-service -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/customer-service/target/customer-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
