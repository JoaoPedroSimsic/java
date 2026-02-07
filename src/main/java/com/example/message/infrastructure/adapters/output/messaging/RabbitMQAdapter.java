package com.example.message.infrastructure.adapters.output.messaging;

import com.example.message.core.events.UserCreatedEvent;
import com.example.message.core.ports.output.MessagePublisherPort;
import com.example.message.infrastructure.config.RabbitConfig;
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
