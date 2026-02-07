package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.events.UserCreatedEvent;

public interface MessagePublisherPort {
  void publish(UserCreatedEvent message);
}
