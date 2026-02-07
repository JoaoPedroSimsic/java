package io.github.joaosimsic.infrastructure.adapters.output.db.repositories;

import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.core.events.UserCreatedEvent;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final MessagePublisherPort messagePublisher;
  private final ObjectMapper objectMapper;

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void processOutbox() {
    var entries = outboxPort.findUnprocessed(20);

    if (entries.isEmpty()) return;

    List<UUID> processedIds = new ArrayList<>();

    for (OutboxEntry entry : entries) {
      try {
        UserCreatedEvent event = objectMapper.readValue(entry.payload(), UserCreatedEvent.class);

        messagePublisher.publish(event);

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
}
