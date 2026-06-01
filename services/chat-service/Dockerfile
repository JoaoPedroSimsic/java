FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY bom/pom.xml bom/
COPY events/pom.xml events/
COPY services/chat-service/pom.xml services/chat-service/

RUN --mount=type=cache,target=/root/.m2 \
  mvn install -N -f pom.xml -DskipTests

RUN --mount=type=cache,target=/root/.m2 \
  mvn install -f bom/pom.xml -DskipTests

COPY events/schemas events/schemas
RUN --mount=type=cache,target=/root/.m2 \
  mvn install -f events/pom.xml -DskipTests

RUN --mount=type=cache,target=/root/.m2 \
  mvn -f services/chat-service/pom.xml dependency:resolve

COPY services/chat-service/src services/chat-service/src
RUN --mount=type=cache,target=/root/.m2 \
  mvn -f services/chat-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

COPY --from=builder /app/services/chat-service/target/*.jar app.jar

RUN mkdir -p /app/logs /app/resources && chown -R appuser:appgroup /app/logs /app/resources

USER appuser
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
