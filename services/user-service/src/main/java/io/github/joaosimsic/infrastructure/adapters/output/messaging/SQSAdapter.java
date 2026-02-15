package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import io.github.joaosimsic.events.user.UserCreatedEvent;
import io.github.joaosimsic.events.user.UserDeletedEvent;
import io.github.joaosimsic.events.user.UserUpdatedEvent;
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
  public void publish(UserCreatedEvent event) {
    sqsTemplate.send("user-created-queue", event);
  }

  @Override
  public void publish(UserUpdatedEvent event) {
    sqsTemplate.send("user-updated-queue", event);
  }

  @Override
  public void publish(UserDeletedEvent event) {
    sqsTemplate.send("user-deleted-queue", event);
  }
}
