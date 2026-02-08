package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import io.github.joaosimsic.core.events.DomainEvent;
import io.github.joaosimsic.core.events.UserCreatedEvent;
import io.github.joaosimsic.core.events.UserDeletedEvent;
import io.github.joaosimsic.core.events.UserUpdatedEvent;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import io.github.joaosimsic.infrastructure.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class RabbitMQAdapter implements MessagePublisherPort {
  private final RabbitTemplate rabbitTemplate;

  public RabbitMQAdapter(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void publish(DomainEvent event) {
    String routingKey = getRoutingKey(event);

    rabbitTemplate.convertAndSend(RabbitConfig.USER_EXCHANGE, routingKey, event);
  }

  private String getRoutingKey(DomainEvent event) {
    return switch (event) {
      case UserCreatedEvent e -> RabbitConfig.USER_CREATED_ROUTING_KEY;
      case UserUpdatedEvent e -> RabbitConfig.USER_UPDATED_ROUTING_KEY;
      case UserDeletedEvent e -> RabbitConfig.USER_DELETED_ROUTING_KEY;
      default -> throw new IllegalArgumentException("Unknown event type: " + event.eventType());
    };
  }
}
