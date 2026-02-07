package io.github.joaosimsic.core.events;

import java.time.Instant;

public record UserCreatedEvent(Long userId, String email, String name, Instant occurredAt)
    implements DomainEvent {

  public UserCreatedEvent(Long userId, String email, String name) {
    this(userId, email, name, Instant.now());
  }

  @Override
  public String eventType() {
    return "USER_CREATED";
  }

  @Override
  public Long aggregateId() {
    return userId;
  }
}
