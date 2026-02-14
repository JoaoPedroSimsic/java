package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.github.joaosimsic.core.events.UserEmailUpdatedEvent;
import io.github.joaosimsic.core.events.UserRegisteredEvent;
import io.github.joaosimsic.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class RabbitMQEventPublisherTest {

  @Mock private RabbitTemplate rabbitTemplate;

  private RabbitMQEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    eventPublisher = new RabbitMQEventPublisher(rabbitTemplate);
  }

  @Nested
  @DisplayName("publishUserRegistered")
  class PublishUserRegistered {

    @Test
    @DisplayName("should send event to correct exchange with correct routing key")
    void shouldSendEventToCorrectExchangeWithCorrectRoutingKey() {
      UserRegisteredEvent event = new UserRegisteredEvent("user-123", "john@example.com", "John Doe");

      eventPublisher.publishUserRegistered(event);

      verify(rabbitTemplate)
          .convertAndSend(
              eq(RabbitConfig.AUTH_EXCHANGE),
              eq(RabbitConfig.USER_REGISTERED_ROUTING_KEY),
              eq(event));
    }

    @Test
    @DisplayName("should use auth.exchange as the exchange name")
    void shouldUseCorrectExchangeName() {
      assertEquals("auth.exchange", RabbitConfig.AUTH_EXCHANGE);
    }

    @Test
    @DisplayName("should use auth.user.registered as the routing key")
    void shouldUseCorrectRoutingKey() {
      assertEquals("auth.user.registered", RabbitConfig.USER_REGISTERED_ROUTING_KEY);
    }

    @Test
    @DisplayName("should pass the complete event object to RabbitTemplate")
    void shouldPassCompleteEventObjectToRabbitTemplate() {
      String externalId = "user-456";
      String email = "test@example.com";
      String name = "Test User";

      UserRegisteredEvent event = new UserRegisteredEvent(externalId, email, name);

      eventPublisher.publishUserRegistered(event);

      ArgumentCaptor<UserRegisteredEvent> eventCaptor =
          ArgumentCaptor.forClass(UserRegisteredEvent.class);
      verify(rabbitTemplate)
          .convertAndSend(
              eq(RabbitConfig.AUTH_EXCHANGE),
              eq(RabbitConfig.USER_REGISTERED_ROUTING_KEY),
              eventCaptor.capture());

      UserRegisteredEvent capturedEvent = eventCaptor.getValue();
      assertEquals(externalId, capturedEvent.externalId());
      assertEquals(email, capturedEvent.email());
      assertEquals(name, capturedEvent.name());
      assertNotNull(capturedEvent.occurredAt());
      assertEquals("USER_REGISTERED", capturedEvent.eventType());
    }
  }

  @Nested
  @DisplayName("publishUserEmailUpdated")
  class PublishUserEmailUpdated {

    @Test
    @DisplayName("should send event to correct exchange with correct routing key")
    void shouldSendEventToCorrectExchangeWithCorrectRoutingKey() {
      UserEmailUpdatedEvent event = new UserEmailUpdatedEvent("user-123", "newemail@example.com");

      eventPublisher.publishUserEmailUpdated(event);

      verify(rabbitTemplate)
          .convertAndSend(
              eq(RabbitConfig.AUTH_EXCHANGE),
              eq(RabbitConfig.USER_EMAIL_UPDATED_ROUTING_KEY),
              eq(event));
    }

    @Test
    @DisplayName("should use auth.user.email.updated as the routing key")
    void shouldUseCorrectRoutingKey() {
      assertEquals("auth.user.email.updated", RabbitConfig.USER_EMAIL_UPDATED_ROUTING_KEY);
    }

    @Test
    @DisplayName("should pass the complete event object to RabbitTemplate")
    void shouldPassCompleteEventObjectToRabbitTemplate() {
      String externalId = "user-789";
      String newEmail = "updated@example.com";

      UserEmailUpdatedEvent event = new UserEmailUpdatedEvent(externalId, newEmail);

      eventPublisher.publishUserEmailUpdated(event);

      ArgumentCaptor<UserEmailUpdatedEvent> eventCaptor =
          ArgumentCaptor.forClass(UserEmailUpdatedEvent.class);
      verify(rabbitTemplate)
          .convertAndSend(
              eq(RabbitConfig.AUTH_EXCHANGE),
              eq(RabbitConfig.USER_EMAIL_UPDATED_ROUTING_KEY),
              eventCaptor.capture());

      UserEmailUpdatedEvent capturedEvent = eventCaptor.getValue();
      assertEquals(externalId, capturedEvent.externalId());
      assertEquals(newEmail, capturedEvent.newEmail());
      assertNotNull(capturedEvent.occurredAt());
      assertEquals("USER_EMAIL_UPDATED", capturedEvent.eventType());
    }
  }
}
