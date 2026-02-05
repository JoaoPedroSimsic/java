package com.example.message.infrastructure.adapters.output.db.repositories;

import com.example.message.core.events.UserCreatedEvent;
import com.example.message.core.exceptions.infrastructure.MessagingException;
import com.example.message.core.ports.output.OutboxPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaOutboxAdapter implements OutboxPort {
  private final JdbcTemplate JdbcTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public void save(UserCreatedEvent event) {
    try {
      String payload = objectMapper.writeValueAsString(event);
      String sql =
          "INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload) VALUES (?, ?,"
              + " ?, ?, ?)";
      JdbcTemplate.update(
          sql, UUID.randomUUID(), "USER", event.userId().toString(), "USER_CREATED", payload);
    } catch (JsonProcessingException e) {
      throw new MessagingException("Failed to serialize event");
    }
  }
}
