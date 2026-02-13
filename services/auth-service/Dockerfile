FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY bom/pom.xml bom/
RUN mvn install -f bom/pom.xml -q

COPY services/auth-service/pom.xml services/auth-service/
RUN mvn dependency:go-offline -f services/auth-service/pom.xml -q

COPY services/auth-service/src services/auth-service/src
RUN mvn package -f services/auth-service/pom.xml -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
COPY --from=builder /app/services/auth-service/target/*.jar app.jar
RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs

USER appuser
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
