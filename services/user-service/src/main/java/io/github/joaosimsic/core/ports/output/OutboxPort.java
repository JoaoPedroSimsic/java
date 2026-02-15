package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.events.user.UserCreatedEvent;
import io.github.joaosimsic.events.user.UserDeletedEvent;
import io.github.joaosimsic.events.user.UserUpdatedEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxPort {

  void save(UserCreatedEvent event);
  void save(UserUpdatedEvent event);
  void save(UserDeletedEvent event);

  List<OutboxEntry> findUnprocessed(int batchSize);

  void markAsProcessed(List<UUID> ids);

  void markAsFailed(UUID id, String reason);

  void incrementAttempt(UUID id, String lastError);
}