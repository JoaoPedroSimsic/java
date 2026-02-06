package com.example.message.infrastructure.adapters.output.messaging;

import com.example.message.core.events.UserCreatedEvent;
import com.example.message.core.ports.output.MessagePublisherPort;

@Component
@Profile("prod")
public class SQSAdapter implements MessagePublisherPort {
  private final SqsTemplate sqsTemplate;

  @Override
  public void publish(UserCreatedEvent event) {
    sqsTemplate.send("user-created-queue", event);
  }
}
