package io.github.joaosimsic.infrastructure;

import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import com.redis.testcontainers.RedisContainer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

  @LocalServerPort protected Integer port;

  @MockitoBean protected MessagePublisherPort messagePublisherPort;

  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine").withReuse(true);

  static final RedisContainer redis =
      new RedisContainer(DockerImageName.parse("redis:7-alpine")).withReuse(true);

  static final RabbitMQContainer rabbitmq =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine")).withReuse(true);

  static {
    postgres.start();
    redis.start();
    rabbitmq.start();
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

    registry.add("spring.rabbitmq.host", rabbitmq::getHost);
    registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
  }

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private CacheManager cacheManager;

  @BeforeEach
  protected void setupBase() {
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    // Clean up before each test to ensure a fresh state (important for reused containers)
    clearAllCaches();
    jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    clearAllCaches();
  }

  private void clearAllCaches() {
    cacheManager.getCacheNames().forEach(name -> {
      var cache = cacheManager.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    });
  }
}
