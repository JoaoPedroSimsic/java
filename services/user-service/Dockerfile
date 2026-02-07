FROM maven:3.8.4-openjdk-17-slim
WORKDIR /app

# Install BOM first (from root build context)
COPY bom/pom.xml /bom/pom.xml
RUN mvn install -f /bom/pom.xml -q

# Copy service pom and download dependencies
COPY services/user-service/pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code
COPY services/user-service/src ./src

CMD [ "mvn", "spring-boot:run" ]
