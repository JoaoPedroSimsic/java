FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY bom/pom.xml bom/
COPY events/pom.xml events/
COPY services/user-service/pom.xml services/user-service/

RUN --mount=type=cache,target=/root/.m2 \
  mvn install -f bom/pom.xml -DskipTests

COPY events/schemas events/schemas
RUN --mount=type=cache,target=/root/.m2 \
  mvn install -f events/pom.xml -DskipTests

RUN --mount=type=cache,target=/root/.m2 \
  mvn -f services/user-service/pom.xml dependency:resolve

COPY services/user-service/src services/user-service/src
RUN --mount=type=cache,target=/root/.m2 \
  mvn -f services/user-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

COPY --from=builder /app/services/user-service/target/*.jar app.jar

RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs

USER appuser
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
