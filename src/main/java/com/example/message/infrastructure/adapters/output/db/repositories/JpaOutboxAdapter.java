package com.example.message.infrastructure.adapters.output.db.repositories;

import com.example.message.core.domain.OutboxEntry;
import com.example.message.core.events.UserCreatedEvent;
import com.example.message.core.exceptions.infrastructure.MessagingException;
import com.example.message.core.ports.output.OutboxPort;
import com.example.message.infrastructure.adapters.output.db.entities.OutboxEntity;
import com.example.message.infrastructure.adapters.output.db.jpa.JpaOutboxRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaOutboxAdapter implements OutboxPort {

    private final JpaOutboxRepo repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void save(UserCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEntity entity = OutboxEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("USER")
                    .aggregateId(event.userId().toString())
                    .eventType("USER_CREATED")
                    .payload(payload)
                    .build();

            repository.save(entity);
        } catch (JsonProcessingException e) {
            throw new MessagingException("Failed to serialize event");
        }
    }

    @Override
    public List<OutboxEntry> findUnprocessed(int batchSize) {
        return repository.findUnprocessed(PageRequest.of(0, batchSize))
                .stream()
                .map(entity -> new OutboxEntry(
                        entity.getId(),
                        entity.getAggregateId(),
                        entity.getAggregateType(),
                        entity.getEventType(),
                        entity.getPayload()
                )).toList();
    }

    @Override
    @Transactional
    public void markAsProcessed(List<UUID> ids) {
        repository.markAsProcessed(ids);
    }

    @Override
    @Transactional
    public void incrementAttempt(UUID id, String lastError) {
        repository.findById(id).ifPresent(entity -> {
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setLastError(lastError);
            repository.save(entity);
        });
    }
}
