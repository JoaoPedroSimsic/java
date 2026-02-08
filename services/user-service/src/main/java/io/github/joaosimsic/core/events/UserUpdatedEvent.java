package io.github.joaosimsic.core.events;

import java.time.Instant;

public record UserUpdatedEvent(Long userId, String email, String name, Instant occurredAt)
    implements DomainEvent {

  public UserUpdatedEvent(Long userId, String email, String name) {
    this(userId, email, name, Instant.now());
  }

  @Override
  public String eventType() {
    return "USER_UPDATED";
  }

  @Override
  public Long aggregateId() {
    return userId;
  }
}
