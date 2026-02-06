package com.example.message.core.ports.output;

import com.example.message.core.domain.OutboxEntry;
import com.example.message.core.events.UserCreatedEvent;
import java.util.List;
import java.util.UUID;

public interface OutboxPort {
  void save(UserCreatedEvent event);

  List<OutboxEntry> findUnprocessed(int batchSize);

  void markAsProcessed(List<UUID> ids);

  void incrementAttempt(UUID id, String lastError);
}
