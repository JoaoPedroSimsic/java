package io.github.joaosimsic.core.events;

import java.time.Instant;

public record AuthUserEmailUpdatedEvent(
    String externalId,
    String newEmail,
    Instant occurredAt
) {
  public String eventType() {
    return "USER_EMAIL_UPDATED";
  }
}
