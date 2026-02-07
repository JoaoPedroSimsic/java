package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import io.github.joaosimsic.core.events.UserCreatedEvent;
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
  public void publish(UserCreatedEvent event) {
    rabbitTemplate.convertAndSend(
        RabbitConfig.USER_EXCHANGE, RabbitConfig.USER_CREATED_ROUTING_QUEUE, event);
  }
}
