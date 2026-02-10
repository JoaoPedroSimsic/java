FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY bom/pom.xml bom/
COPY gateway/pom.xml gateway/

RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl gateway -am -B

COPY gateway/src gateway/src

RUN ./mvnw package -pl gateway -am -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1001 appgroup && \
  adduser -u 1001 -G appgroup -D appuser

COPY --from=builder /app/gateway/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
