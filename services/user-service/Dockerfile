FROM maven:3.9.6-eclipse-temurin-21-alpine
WORKDIR /app

COPY bom/pom.xml /bom/pom.xml
RUN mvn install -f /bom/pom.xml -q

COPY services/user-service/pom.xml .
RUN mvn dependency:go-offline -q

COPY services/user-service/src ./src

CMD [ "mvn", "spring-boot:run" ]
