package com.example.message.infrastructure;

import com.redis.testcontainers.RedisContainer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

  @LocalServerPort protected Integer port;

  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

  static {
    postgres.start();
    redis.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);

    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
  }

  @BeforeEach
  protected void setupBase() {
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
  }

  @AfterEach
  void cleanUp(@Autowired JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
  }
}
