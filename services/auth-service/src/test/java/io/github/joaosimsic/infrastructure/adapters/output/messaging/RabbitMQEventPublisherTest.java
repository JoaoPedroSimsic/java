package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
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
      var event =
          new UserRegisteredEvent()
              .withExternalId("user-123")
              .withEmail("john@example.com")
              .withName("John Doe");

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

      var event =
          new UserRegisteredEvent().withExternalId(externalId).withEmail(email).withName(name);

      eventPublisher.publishUserRegistered(event);

      ArgumentCaptor<UserRegisteredEvent> eventCaptor =
          ArgumentCaptor.forClass(UserRegisteredEvent.class);
      verify(rabbitTemplate)
          .convertAndSend(
              eq(RabbitConfig.AUTH_EXCHANGE),
              eq(RabbitConfig.USER_REGISTERED_ROUTING_KEY),
              eventCaptor.capture());

      UserRegisteredEvent capturedEvent = eventCaptor.getValue();
      assertEquals(externalId, capturedEvent.getExternalId());
      assertEquals(email, capturedEvent.getEmail());
      assertEquals(name, capturedEvent.getName());
      assertNotNull(capturedEvent.getOccurredAt());
      assertEquals("USER_REGISTERED", capturedEvent.getEventType());
    }
  }

  @Nested
  @DisplayName("publishUserEmailUpdated")
  class PublishUserEmailUpdated {

    @Test
    @DisplayName("should send event to correct exchange with correct routing key")
    void shouldSendEventToCorrectExchangeWithCorrectRoutingKey() {

      var event =
          new EmailUpdatedEvent().withExternalId("user-123").withNewEmail("john@example.com");

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

      var event = new EmailUpdatedEvent().withExternalId(externalId).withNewEmail(newEmail);

      eventPublisher.publishUserEmailUpdated(event);

      ArgumentCaptor<EmailUpdatedEvent> eventCaptor =
          ArgumentCaptor.forClass(EmailUpdatedEvent.class);
      verify(rabbitTemplate)
          .convertAndSend(
              eq(RabbitConfig.AUTH_EXCHANGE),
              eq(RabbitConfig.USER_EMAIL_UPDATED_ROUTING_KEY),
              eventCaptor.capture());

      EmailUpdatedEvent capturedEvent = eventCaptor.getValue();
      assertEquals(externalId, capturedEvent.getExternalId());
      assertEquals(newEmail, capturedEvent.getNewEmail());
      assertNotNull(capturedEvent.getOccurredAt());
      assertEquals("USER_EMAIL_UPDATED", capturedEvent.getEventType());
    }
  }
}
