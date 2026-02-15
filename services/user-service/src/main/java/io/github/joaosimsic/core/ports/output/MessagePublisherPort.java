package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.events.user.UserCreatedEvent;
import io.github.joaosimsic.events.user.UserDeletedEvent;
import io.github.joaosimsic.events.user.UserUpdatedEvent;

public interface MessagePublisherPort {
  void publish(UserCreatedEvent event);
  void publish(UserUpdatedEvent event);
  void publish(UserDeletedEvent event);
}