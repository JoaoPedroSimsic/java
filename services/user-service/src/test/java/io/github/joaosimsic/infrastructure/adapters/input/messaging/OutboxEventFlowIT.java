package io.github.joaosimsic.infrastructure.adapters.input.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
import io.github.joaosimsic.infrastructure.config.RabbitConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Outbox Pattern Event-Driven Flow Integration Tests")
class OutboxEventFlowIT {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
  private static final Duration MAX_WAIT_TIME = Duration.ofSeconds(30);

  record OutboxRecord(
      UUID id,
      String aggregateType,
      String aggregateId,
      String eventType,
      String payload,
      String status,
      boolean processed,
      int attempts) {}

  record UserRecord(Long id, String externalId, String name, String email, Instant createdAt) {}

  @Container
  @ServiceConnection
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("outbox_test_db")
          .withUsername("outbox_test_user")
          .withPassword("outbox_test_pass");

  @Container @ServiceConnection
  static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

  @Container @ServiceConnection
  static final RabbitMQContainer rabbitmq =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));

  static {
    postgres.start();
    redis.start();
    rabbitmq.start();
  }

  @MockitoBean private MessagePublisherPort messagePublisherPort;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CacheManager cacheManager;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private RabbitAdmin rabbitAdmin;
  @Autowired private RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws InterruptedException {
    ensureListenersAreRunning();
    clearAllCaches();
    purgeRabbitQueues();
    jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE outbox CASCADE");
    Thread.sleep(200);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE outbox CASCADE");
    clearAllCaches();
  }

  @Test
  @DisplayName(
      "UserRegisteredEvent: Should persist to outbox, publish to RabbitMQ, and create user profile")
  void shouldProcessUserRegisteredEventThroughEntireFlow() throws Exception {
    var externalId = UUID.randomUUID().toString();
    var email = "test-" + System.currentTimeMillis() + "@example.com";
    var name = "Test User";

    var event =
        new UserRegisteredEvent()
            .withExternalId(externalId)
            .withEmail(email)
            .withName(name)
            .withOccurredAt(Instant.now())
            .withEventType("USER_REGISTERED");

    var outboxId = UUID.randomUUID();
    var payload = objectMapper.writeValueAsString(event);

    jdbcTemplate.update(
        """
        INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload, status, processed, attempts, created_at)
        VALUES (?, 'AUTH', ?, 'USER_REGISTERED', ?::jsonb, 'PENDING', false, 0, CURRENT_TIMESTAMP)
        """,
        outboxId,
        externalId,
        payload);

    var outboxRecord = getOutboxRecord(outboxId);
    assertThat(outboxRecord).isNotNull();
    assertThat(outboxRecord.status()).isEqualTo("PENDING");
    assertThat(outboxRecord.eventType()).isEqualTo("USER_REGISTERED");
    assertThat(outboxRecord.payload()).contains(externalId);
    assertThat(outboxRecord.payload()).contains(email);

    rabbitTemplate.convertAndSend(
        RabbitConfig.AUTH_EXCHANGE, RabbitConfig.AUTH_USER_REGISTERED_ROUTING_KEY, event);

    await()
        .atMost(MAX_WAIT_TIME)
        .pollInterval(POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var user = findUserByExternalId(externalId);
              assertThat(user).as("User with externalId %s should exist", externalId).isNotNull();
              assertThat(user.email()).isEqualTo(email);
              assertThat(user.name()).isEqualTo(name);
              assertThat(user.externalId()).isEqualTo(externalId);
            });

    var userRecord = findUserByExternalId(externalId);
    assertThat(userRecord).isNotNull();
    assertThat(userRecord.email()).isEqualTo(email);
    assertThat(userRecord.name()).isEqualTo(name);
  }

  @Test
  @DisplayName("Should verify outbox entry contains correct payload structure")
  void shouldPersistCorrectOutboxPayload() throws Exception {
    var externalId = UUID.randomUUID().toString();
    var email = "payload-test-" + System.currentTimeMillis() + "@example.com";
    var name = "Payload Test User";
    var occurredAt = Instant.now();

    var event =
        new UserRegisteredEvent()
            .withExternalId(externalId)
            .withEmail(email)
            .withName(name)
            .withOccurredAt(occurredAt)
            .withEventType("USER_REGISTERED");

    var outboxId = UUID.randomUUID();
    var payload = objectMapper.writeValueAsString(event);

    jdbcTemplate.update(
        """
        INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload, status, processed, attempts, created_at)
        VALUES (?, 'AUTH', ?, 'USER_REGISTERED', ?::jsonb, 'PENDING', false, 0, CURRENT_TIMESTAMP)
        """,
        outboxId,
        externalId,
        payload);

    var outboxRecord = getOutboxRecord(outboxId);
    assertThat(outboxRecord).isNotNull();
    assertThat(outboxRecord.status()).isEqualTo("PENDING");
    assertThat(outboxRecord.processed()).isFalse();
    assertThat(outboxRecord.attempts()).isEqualTo(0);
    assertThat(outboxRecord.aggregateType()).isEqualTo("AUTH");
    assertThat(outboxRecord.aggregateId()).isEqualTo(externalId);
    assertThat(outboxRecord.eventType()).isEqualTo("USER_REGISTERED");

    var deserializedEvent =
        objectMapper.readValue(outboxRecord.payload(), UserRegisteredEvent.class);
    assertThat(deserializedEvent.getExternalId()).isEqualTo(externalId);
    assertThat(deserializedEvent.getEmail()).isEqualTo(email);
    assertThat(deserializedEvent.getName()).isEqualTo(name);
    assertThat(deserializedEvent.getEventType()).isEqualTo("USER_REGISTERED");
  }

  @Test
  @DisplayName("Should handle idempotent event consumption - duplicate events should not fail")
  void shouldHandleIdempotentConsumption() {
    var externalId = UUID.randomUUID().toString();
    var email = "idempotent-" + System.currentTimeMillis() + "@example.com";
    var name = "Idempotent User";

    var event =
        new UserRegisteredEvent()
            .withExternalId(externalId)
            .withEmail(email)
            .withName(name)
            .withOccurredAt(Instant.now())
            .withEventType("USER_REGISTERED");

    rabbitTemplate.convertAndSend(
        RabbitConfig.AUTH_EXCHANGE, RabbitConfig.AUTH_USER_REGISTERED_ROUTING_KEY, event);

    await()
        .atMost(MAX_WAIT_TIME)
        .pollInterval(POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var user = findUserByExternalId(externalId);
              assertThat(user).as("User with externalId %s should exist", externalId).isNotNull();
            });

    rabbitTemplate.convertAndSend(
        RabbitConfig.AUTH_EXCHANGE, RabbitConfig.AUTH_USER_REGISTERED_ROUTING_KEY, event);

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var count = countUsersByExternalId(externalId);
              assertThat(count).isEqualTo(1);
            });
  }

  @Test
  @DisplayName("Should verify data integrity between published event and consumed entity")
  void shouldMaintainDataIntegrityBetweenEventAndEntity() {
    var externalId = UUID.randomUUID().toString();
    var email = "integrity-" + System.currentTimeMillis() + "@example.com";
    var name = "Data Integrity User";

    var event =
        new UserRegisteredEvent()
            .withExternalId(externalId)
            .withEmail(email)
            .withName(name)
            .withOccurredAt(Instant.now())
            .withEventType("USER_REGISTERED");

    rabbitTemplate.convertAndSend(
        RabbitConfig.AUTH_EXCHANGE, RabbitConfig.AUTH_USER_REGISTERED_ROUTING_KEY, event);

    await()
        .atMost(MAX_WAIT_TIME)
        .pollInterval(POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var user = findUserByExternalId(externalId);
              assertThat(user).as("User with externalId %s should exist", externalId).isNotNull();
              assertThat(user.externalId()).isEqualTo(event.getExternalId());
              assertThat(user.email()).isEqualTo(event.getEmail());
              assertThat(user.name()).isEqualTo(event.getName());
              assertThat(user.id()).isNotNull();
              assertThat(user.createdAt()).isNotNull();
            });
  }

  @Test
  @DisplayName("Should process multiple concurrent events maintaining order and consistency")
  void shouldProcessMultipleConcurrentEvents() {
    var eventCount = 5;
    var baseTimestamp = System.currentTimeMillis();

    for (int i = 0; i < eventCount; i++) {
      var event =
          new UserRegisteredEvent()
              .withExternalId(UUID.randomUUID().toString())
              .withEmail("concurrent-" + baseTimestamp + "-" + i + "@example.com")
              .withName("Concurrent User " + i)
              .withOccurredAt(Instant.now())
              .withEventType("USER_REGISTERED");

      rabbitTemplate.convertAndSend(
          RabbitConfig.AUTH_EXCHANGE, RabbitConfig.AUTH_USER_REGISTERED_ROUTING_KEY, event);
    }

    await()
        .atMost(MAX_WAIT_TIME)
        .pollInterval(POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var count = countUsersWithEmailPattern("concurrent-" + baseTimestamp + "%");
              assertThat(count).isEqualTo(eventCount);
            });
  }

  private OutboxRecord getOutboxRecord(UUID id) {
    var records =
        jdbcTemplate.query(
            "SELECT * FROM outbox WHERE id = ?",
            (rs, rowNum) ->
                new OutboxRecord(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("aggregate_type"),
                    rs.getString("aggregate_id"),
                    rs.getString("event_type"),
                    rs.getString("payload"),
                    rs.getString("status"),
                    rs.getBoolean("processed"),
                    rs.getInt("attempts")),
            id);
    return records.isEmpty() ? null : records.get(0);
  }

  private UserRecord findUserByExternalId(String externalId) {
    var users =
        jdbcTemplate.query(
            "SELECT * FROM users WHERE external_id = ?",
            (rs, rowNum) ->
                new UserRecord(
                    rs.getLong("id"),
                    rs.getString("external_id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getTimestamp("created_at").toInstant()),
            externalId);
    return users.isEmpty() ? null : users.get(0);
  }

  private int countUsersByExternalId(String externalId) {
    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE external_id = ?", Integer.class, externalId);
    return count != null ? count : 0;
  }

  private int countUsersWithEmailPattern(String pattern) {
    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email LIKE ?", Integer.class, pattern);
    return count != null ? count : 0;
  }

  private void clearAllCaches() {
    cacheManager
        .getCacheNames()
        .forEach(
            name -> {
              var cache = cacheManager.getCache(name);
              if (cache != null) {
                cache.clear();
              }
            });
  }

  private void purgeRabbitQueues() {
    try {
      rabbitAdmin.purgeQueue(RabbitConfig.AUTH_USER_REGISTERED_QUEUE, false);
      rabbitAdmin.purgeQueue(RabbitConfig.AUTH_USER_EMAIL_UPDATED_QUEUE, false);
    } catch (Exception e) {
      // Queue may not exist yet, ignore
    }
  }

  private void ensureListenersAreRunning() {
    rabbitListenerEndpointRegistry
        .getListenerContainers()
        .forEach(
            container -> {
              if (!container.isRunning()) {
                container.start();
              }
            });
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                rabbitListenerEndpointRegistry.getListenerContainers().stream()
                    .allMatch(c -> c.isRunning()));
  }
}
