package io.github.joaosimsic.core.events;

import java.time.Instant;

public record UserEmailUpdatedEvent(
    String externalId,
    String newEmail,
    Instant occurredAt
) {
  public UserEmailUpdatedEvent(String externalId, String newEmail) {
    this(externalId, newEmail, Instant.now());
  }

  public String eventType() {
    return "USER_EMAIL_UPDATED";
  }
}
