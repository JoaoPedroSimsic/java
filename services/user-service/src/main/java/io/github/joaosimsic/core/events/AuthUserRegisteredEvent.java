package io.github.joaosimsic.core.events;

import java.time.Instant;

public record AuthUserRegisteredEvent(
    String externalId,
    String email,
    String name,
    Instant occurredAt
) {
  public String eventType() {
    return "USER_REGISTERED";
  }
}
