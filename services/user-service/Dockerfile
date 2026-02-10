FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY bom/pom.xml bom/
RUN mvn install -f bom/pom.xml -q

COPY services/user-service/pom.xml services/user-service/
RUN mvn dependency:go-offline -f services/user-service/pom.xml -q

COPY services/user-service/src services/user-service/src
RUN mvn package -f services/user-service/pom.xml -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
COPY --from=builder /app/services/user-service/target/*.jar app.jar

USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
