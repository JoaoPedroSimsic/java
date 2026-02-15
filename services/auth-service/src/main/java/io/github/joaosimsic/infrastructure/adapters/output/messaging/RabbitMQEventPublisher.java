package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import io.github.joaosimsic.core.ports.output.EventPublisherPort;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.infrastructure.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements EventPublisherPort {

  private final RabbitTemplate rabbitTemplate;

  @Override
  public void publishUserRegistered(UserRegisteredEvent event) {
    log.info("Publishing user registered event for user: {}", event.getExternalId());
    rabbitTemplate.convertAndSend(
        RabbitConfig.AUTH_EXCHANGE, RabbitConfig.USER_REGISTERED_ROUTING_KEY, event);
    log.debug("User registered event published successfully");
  }

  @Override
  public void publishUserEmailUpdated(EmailUpdatedEvent event) {
    log.info("Publishing user email updated event for user: {}", event.getExternalId());
    rabbitTemplate.convertAndSend(
        RabbitConfig.AUTH_EXCHANGE, RabbitConfig.USER_EMAIL_UPDATED_ROUTING_KEY, event);
    log.debug("User email updated event published successfully");
  }
}
