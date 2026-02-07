package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.core.events.DomainEvent;
import java.util.List;
import java.util.UUID;

public interface OutboxPort {
  void save(DomainEvent event);

  List<OutboxEntry> findUnprocessed(int batchSize);

  void markAsProcessed(List<UUID> ids);

  void incrementAttempt(UUID id, String lastError);
}
