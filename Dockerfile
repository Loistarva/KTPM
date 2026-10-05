FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend/src src
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S ktpm && adduser -S ktpm -G ktpm
COPY --from=build /app/target/ktpm-backend-1.0.0.jar app.jar
USER ktpm
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
