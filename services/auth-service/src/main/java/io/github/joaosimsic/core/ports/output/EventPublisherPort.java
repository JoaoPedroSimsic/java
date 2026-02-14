package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.events.UserEmailUpdatedEvent;
import io.github.joaosimsic.core.events.UserRegisteredEvent;

public interface EventPublisherPort {
  void publishUserRegistered(UserRegisteredEvent event);

  void publishUserEmailUpdated(UserEmailUpdatedEvent event);
}
