package io.github.joaosimsic.infrastructure.adapters.output.db.jpa;

import io.github.joaosimsic.infrastructure.adapters.output.db.entities.OutboxEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaOutboxRepo extends JpaRepository<OutboxEntity, UUID> {

  @Query(
      "SELECT o FROM OutboxEntity o WHERE o.processed = false AND o.attempts < 5 ORDER BY"
          + " o.createdAt ASC")
  List<OutboxEntity> findUnprocessed(Pageable pageable);

  @Modifying
  @Query(
      "UPDATE OutboxEntity o SET o.processed = true, o.status = 'PROCESSED', o.processedAt ="
          + " CURRENT_TIMESTAMP WHERE o.id IN :ids")
  void markAsProcessed(@Param("ids") List<UUID> ids);
}

