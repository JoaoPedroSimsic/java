package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.events.DomainEvent;

public interface MessagePublisherPort {
  void publish(DomainEvent event);
}
