package io.github.joaosimsic.infrastructure.adapters.output.db.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEntity {

  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private OutboxStatus status = OutboxStatus.PENDING;

  @Builder.Default private boolean processed = false;

  @Builder.Default private int attempts = 0;

  @Column(name = "last_error")
  private String lastError;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;
}
