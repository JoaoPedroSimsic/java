FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY bom/pom.xml bom/
RUN mvn install -f bom/pom.xml -q

COPY gateway/pom.xml gateway/
RUN mvn dependency:go-offline -f gateway/pom.xml -q

COPY gateway/src gateway/src
RUN mvn package -f gateway/pom.xml -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
COPY --from=builder /app/gateway/target/*.jar app.jar
RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
