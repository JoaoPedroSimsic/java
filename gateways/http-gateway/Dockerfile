FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY bom/pom.xml bom/
RUN --mount=type=cache,target=/root/.m2 \
    mvn install -N -f pom.xml -DskipTests -q
RUN --mount=type=cache,target=/root/.m2 \
    mvn install -f bom/pom.xml -DskipTests -q

COPY gateways/http-gateway/pom.xml gateways/http-gateway/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f gateways/http-gateway/pom.xml dependency:go-offline -q

COPY gateways/http-gateway/src gateways/http-gateway/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f gateways/http-gateway/pom.xml package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

COPY --from=builder /app/gateways/http-gateway/target/*.jar app.jar

RUN mkdir -p /app/logs /app/resources && chown -R appuser:appgroup /app/logs /app/resources

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
