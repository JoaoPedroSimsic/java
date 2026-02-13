package io.github.joaosimsic.core.events;

import java.time.Instant;

public record UserRegisteredEvent(
    String externalId,
    String email,
    String name,
    Instant occurredAt
) {
  public UserRegisteredEvent(String externalId, String email, String name) {
    this(externalId, email, name, Instant.now());
  }

  public String eventType() {
    return "USER_REGISTERED";
  }
}
