package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.github.joaosimsic.core.events.DomainEvent;
import io.github.joaosimsic.core.events.UserCreatedEvent;
import io.github.joaosimsic.core.events.UserDeletedEvent;
import io.github.joaosimsic.core.events.UserUpdatedEvent;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class SQSAdapter implements MessagePublisherPort {
  private final SqsTemplate sqsTemplate;

  public SQSAdapter(SqsTemplate sqsTemplate) {
    this.sqsTemplate = sqsTemplate;
  }

  @Override
  public void publish(DomainEvent event) {
    String queueName = getQueueName(event);

    sqsTemplate.send(queueName, event);
  }

  private String getQueueName(DomainEvent event) {
    return switch (event) {
      case UserCreatedEvent e -> "user-created-queue";
      case UserUpdatedEvent e -> "user-updated-queue";
      case UserDeletedEvent e -> "user-deleted-queue";
      default -> throw new IllegalArgumentException("Unknown event type: " + event.eventType());
    };
  }
}
