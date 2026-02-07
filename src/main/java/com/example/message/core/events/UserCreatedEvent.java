package com.example.message.core.events;

import java.time.Instant;

public record UserCreatedEvent(Long userId, String email, String name, Instant occurredAt) {
  public UserCreatedEvent(Long userId, String email, String name) {
    this(userId, email, name, Instant.now());
  }
}
