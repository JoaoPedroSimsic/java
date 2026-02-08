package io.github.joaosimsic.core.events;

import java.time.Instant;

public interface DomainEvent {
  String eventType();

  Instant occurredAt();

  Long aggregateId();
}
