package io.github.joaosimsic.infrastructure.adapters.output.db.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.core.events.DomainEvent;
import io.github.joaosimsic.core.exceptions.infrastructure.MessagingException;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.infrastructure.adapters.output.db.entities.OutboxEntity;
import io.github.joaosimsic.infrastructure.adapters.output.db.entities.OutboxStatus;
import io.github.joaosimsic.infrastructure.adapters.output.db.jpa.JpaOutboxRepo;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaOutboxAdapter implements OutboxPort {

  private final JpaOutboxRepo repository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public void save(DomainEvent event) {
    try {
      String payload = objectMapper.writeValueAsString(event);

      OutboxEntity entity =
          OutboxEntity.builder()
              .id(UUID.randomUUID())
              .aggregateType("USER")
              .aggregateId(event.aggregateId().toString())
              .eventType(event.eventType())
              .payload(payload)
              .build();

      repository.save(entity);
    } catch (JsonProcessingException e) {
      throw new MessagingException("Failed to serialize event");
    }
  }

  @Override
  public List<OutboxEntry> findUnprocessed(int batchSize) {
    return repository.findUnprocessed(PageRequest.of(0, batchSize)).stream()
        .map(
            entity ->
                new OutboxEntry(
                    entity.getId(),
                    entity.getAggregateId(),
                    entity.getAggregateType(),
                    entity.getEventType(),
                    entity.getPayload(),
                    entity.getAttempts()))
        .toList();
  }

  @Override
  @Transactional
  public void markAsProcessed(List<UUID> ids) {
    repository.markAsProcessed(ids);
  }

  @Override
  @Transactional
  public void incrementAttempt(UUID id, String lastError) {
    repository
        .findById(id)
        .ifPresent(
            entity -> {
              entity.setAttempts(entity.getAttempts() + 1);
              entity.setLastError(lastError);
              repository.save(entity);
            });
  }

  @Override
  @Transactional
  public void markAsFailed(UUID id, String reason) {
    repository
        .findById(id)
        .ifPresent(
            entity -> {
              entity.setProcessed(true);
              entity.setLastError("FAILED: " + reason);
              entity.setStatus(OutboxStatus.FAILED);
              repository.save(entity);
            });
  }
}
