package io.github.joaosimsic.infrastructure.adapters.output.messaging;

import io.github.joaosimsic.core.events.UserCreatedEvent;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import io.awspring.cloud.sqs.operations.SqsTemplate;
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
}
