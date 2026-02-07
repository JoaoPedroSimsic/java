package io.github.joaosimsic.core.events;

import java.time.Instant;

public record UserDeletedEvent(Long userId, Instant occurredAt) implements DomainEvent {

  public UserDeletedEvent(Long userId) {
    this(userId, Instant.now());
  }

  @Override
  public String eventType() {
    return "USER_DELETED";
  }

  @Override
  public Long aggregateId() {
    return userId;
  }
}
