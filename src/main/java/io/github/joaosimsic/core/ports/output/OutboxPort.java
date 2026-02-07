package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.core.events.UserCreatedEvent;
import java.util.List;
import java.util.UUID;

public interface OutboxPort {
  void save(UserCreatedEvent event);

  List<OutboxEntry> findUnprocessed(int batchSize);

  void markAsProcessed(List<UUID> ids);

  void incrementAttempt(UUID id, String lastError);
}
