package io.github.joaosimsic.infrastructure.adapters.output.db.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.core.ports.output.EventPublisherPort;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
import io.github.joaosimsic.infrastructure.config.properties.OutboxProperties;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

  private final OutboxPort outboxPort;
  private final EventPublisherPort messagePublisher;
  private final ObjectMapper objectMapper;
  private final OutboxProperties outboxProperties;

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
  @Transactional
  public void processOutbox() {
    var entries = outboxPort.findUnprocessed(outboxProperties.batchSize());

    if (entries.isEmpty()) return;

    List<UUID> processedIds = new ArrayList<>();

    for (OutboxEntry entry : entries) {
      if (entry.attempts() >= outboxProperties.maxAttempts()) {
        outboxPort.markAsFailed(entry.id(), "Max attempts reached");
        continue;
      }

      try {
        Object event = deserializeEvent(entry);

        switch (entry.eventType()) {
          case "USER_REGISTERED" ->
              messagePublisher.publishUserRegistered((UserRegisteredEvent) event);
          case "USER_EMAIL_UPDATED" ->
              messagePublisher.publishUserEmailUpdated((EmailUpdatedEvent) event);
          default -> throw new IllegalArgumentException("Unknown event type: " + entry.eventType());
        }

        processedIds.add(entry.id());
      } catch (Exception e) {
        log.error("Failed to relay event {}: {}", entry.id(), e.getMessage());
        outboxPort.incrementAttempt(entry.id(), e.getMessage());
      }
    }

    if (!processedIds.isEmpty()) {
      outboxPort.markAsProcessed(processedIds);
      log.info("Successfully relayed {} events", processedIds.size());
    }
  }

  private Object deserializeEvent(OutboxEntry entry) throws Exception {
    return switch (entry.eventType()) {
      case "USER_REGISTERED" -> objectMapper.readValue(entry.payload(), UserRegisteredEvent.class);
      case "USER_EMAIL_UPDATED" -> objectMapper.readValue(entry.payload(), EmailUpdatedEvent.class);
      default -> throw new IllegalArgumentException("Unknown event type: " + entry.eventType());
    };
  }
}
