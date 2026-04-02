package io.github.joaosimsic;

import io.github.joaosimsic.infrastructure.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"USER_SERVICE_PORT=0"})
@Testcontainers
@ActiveProfiles("test")
class UserApplicationTests extends BaseIntegrationTest {

  @Container @ServiceConnection
  static RabbitMQContainer rabbitMQContainer =
      new RabbitMQContainer("rabbitmq:3-management-alpine");

  @Test
  void contextLoads() {}
}
