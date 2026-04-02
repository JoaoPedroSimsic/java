package io.github.joaosimsic.infrastructure.adapters.output.db.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.joaosimsic.core.domain.OutboxEntry;
import io.github.joaosimsic.core.ports.output.MessagePublisherPort;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.events.user.UserCreatedEvent;
import io.github.joaosimsic.events.user.UserDeletedEvent;
import io.github.joaosimsic.events.user.UserUpdatedEvent;
import io.github.joaosimsic.infrastructure.config.properties.OutboxProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  @Mock private OutboxPort outboxPort;

  @Mock private MessagePublisherPort messagePublisher;

  @Captor private ArgumentCaptor<List<UUID>> idsCaptor;

  private ObjectMapper objectMapper;

  private OutboxRelay outboxRelay;

  private OutboxProperties outboxProperties;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());

    outboxProperties = new OutboxProperties(20, 5, 1L);

    outboxRelay = new OutboxRelay(outboxPort, messagePublisher, objectMapper, outboxProperties);
  }

  @Nested
  @DisplayName("processOutbox")
  class ProcessOutbox {

    @Test
    @DisplayName("should do nothing when no unprocessed entries")
    void shouldDoNothingWhenNoUnprocessedEntries() {
      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of());

      outboxRelay.processOutbox();

      verify(messagePublisher, never()).publish(any(UserCreatedEvent.class));
      verify(messagePublisher, never()).publish(any(UserUpdatedEvent.class));
      verify(messagePublisher, never()).publish(any(UserDeletedEvent.class));
      verify(outboxPort, never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("should stop processing when max attempts reached")
    void shouldStopProcessingWhenMaxAttemptsReached() {
      UUID entryId = UUID.randomUUID();

      OutboxEntry entry = new OutboxEntry(entryId, "1", "User", "USER_CREATED", "{}", 5);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));

      outboxRelay.processOutbox();

      verify(outboxPort).markAsFailed(eq(entryId), anyString());
      verify(messagePublisher, never()).publish(any(UserCreatedEvent.class));
    }

    @Test
    @DisplayName("should deserialize and publish USER_CREATED event")
    void shouldDeserializeAndPublishUserCreatedEvent() throws Exception {
      UUID entryId = UUID.randomUUID();
      Instant occurredAt = Instant.parse("2026-02-07T10:00:00Z");
      String payload =
          """
          {"aggregateId":1,"email":"john@example.com","name":"John Doe","occurredAt":"2026-02-07T10:00:00Z","eventType":"USER_CREATED"}
          """;

      OutboxEntry entry = new OutboxEntry(entryId, "1", "User", "USER_CREATED", payload, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));

      outboxRelay.processOutbox();

      ArgumentCaptor<UserCreatedEvent> eventCaptor =
          ArgumentCaptor.forClass(UserCreatedEvent.class);
      verify(messagePublisher).publish(eventCaptor.capture());

      UserCreatedEvent event = eventCaptor.getValue();
      assertEquals(1L, event.getAggregateId());
      assertEquals("john@example.com", event.getEmail());
      assertEquals("John Doe", event.getName());
      assertEquals(occurredAt, event.getOccurredAt());

      verify(outboxPort).markAsProcessed(List.of(entryId));
    }

    @Test
    @DisplayName("should deserialize and publish USER_UPDATED event")
    void shouldDeserializeAndPublishUserUpdatedEvent() throws Exception {
      UUID entryId = UUID.randomUUID();
      Instant occurredAt = Instant.parse("2026-02-07T11:00:00Z");
      String payload =
          """
          {"aggregateId":2,"email":"jane.updated@example.com","name":"Jane Updated","occurredAt":"2026-02-07T11:00:00Z","eventType":"USER_UPDATED"}
          """;

      OutboxEntry entry = new OutboxEntry(entryId, "2", "User", "USER_UPDATED", payload, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));

      outboxRelay.processOutbox();

      ArgumentCaptor<UserUpdatedEvent> eventCaptor =
          ArgumentCaptor.forClass(UserUpdatedEvent.class);
      verify(messagePublisher).publish(eventCaptor.capture());

      UserUpdatedEvent event = eventCaptor.getValue();
      assertEquals(2L, event.getAggregateId());
      assertEquals("jane.updated@example.com", event.getEmail());
      assertEquals("Jane Updated", event.getName());
      assertEquals(occurredAt, event.getOccurredAt());

      verify(outboxPort).markAsProcessed(List.of(entryId));
    }

    @Test
    @DisplayName("should deserialize and publish USER_DELETED event")
    void shouldDeserializeAndPublishUserDeletedEvent() throws Exception {
      UUID entryId = UUID.randomUUID();
      Instant occurredAt = Instant.parse("2026-02-07T12:00:00Z");
      String payload =
          """
          {"aggregateId":3,"occurredAt":"2026-02-07T12:00:00Z","eventType":"USER_DELETED"}
          """;

      OutboxEntry entry = new OutboxEntry(entryId, "3", "User", "USER_DELETED", payload, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));

      outboxRelay.processOutbox();

      ArgumentCaptor<UserDeletedEvent> eventCaptor =
          ArgumentCaptor.forClass(UserDeletedEvent.class);
      verify(messagePublisher).publish(eventCaptor.capture());

      UserDeletedEvent event = eventCaptor.getValue();
      assertEquals(3L, event.getAggregateId());
      assertEquals(occurredAt, event.getOccurredAt());

      verify(outboxPort).markAsProcessed(List.of(entryId));
    }

    @Test
    @DisplayName("should increment attempt on unknown event type")
    void shouldIncrementAttemptOnUnknownEventType() {
      UUID entryId = UUID.randomUUID();
      String payload =
          """
          {"userId":1}
          """;

      OutboxEntry entry = new OutboxEntry(entryId, "1", "User", "UNKNOWN_EVENT", payload, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));

      outboxRelay.processOutbox();

      verify(messagePublisher, never()).publish(any(UserCreatedEvent.class));
      verify(outboxPort).incrementAttempt(eq(entryId), anyString());
      verify(outboxPort, never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("should increment attempt on invalid JSON payload")
    void shouldIncrementAttemptOnInvalidJsonPayload() {
      UUID entryId = UUID.randomUUID();
      String invalidPayload = "{ invalid json }";

      OutboxEntry entry = new OutboxEntry(entryId, "1", "User", "USER_CREATED", invalidPayload, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));

      outboxRelay.processOutbox();

      verify(messagePublisher, never()).publish(any(UserCreatedEvent.class));
      verify(outboxPort).incrementAttempt(eq(entryId), anyString());
      verify(outboxPort, never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("should increment attempt when publisher fails")
    void shouldIncrementAttemptWhenPublisherFails() {
      UUID entryId = UUID.randomUUID();
      String payload =
          """
          {"aggregateId":1,"email":"john@example.com","name":"John Doe","occurredAt":"2026-02-07T10:00:00Z","eventType":"USER_CREATED"}
          """;

      OutboxEntry entry = new OutboxEntry(entryId, "1", "User", "USER_CREATED", payload, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize())).thenReturn(List.of(entry));
      doThrow(new RuntimeException("Connection failed"))
          .when(messagePublisher)
          .publish(any(UserCreatedEvent.class));

      outboxRelay.processOutbox();

      verify(outboxPort).incrementAttempt(eq(entryId), anyString());
      verify(outboxPort, never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("should process multiple entries and mark all successful as processed")
    void shouldProcessMultipleEntriesAndMarkSuccessfulAsProcessed() {
      UUID entryId1 = UUID.randomUUID();
      UUID entryId2 = UUID.randomUUID();
      UUID entryId3 = UUID.randomUUID();

      String payload1 =
          """
          {"aggregateId":1,"email":"user1@example.com","name":"User One","occurredAt":"2026-02-07T10:00:00Z","eventType":"USER_CREATED"}
          """;
      String payload2 =
          """
          {"aggregateId":2,"email":"user2@example.com","name":"User Two","occurredAt":"2026-02-07T11:00:00Z","eventType":"USER_UPDATED"}
          """;
      String payload3 =
          """
          {"aggregateId":3,"occurredAt":"2026-02-07T12:00:00Z","eventType":"USER_DELETED"}
          """;

      OutboxEntry entry1 = new OutboxEntry(entryId1, "1", "User", "USER_CREATED", payload1, 0);
      OutboxEntry entry2 = new OutboxEntry(entryId2, "2", "User", "USER_UPDATED", payload2, 0);
      OutboxEntry entry3 = new OutboxEntry(entryId3, "3", "User", "USER_DELETED", payload3, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize()))
          .thenReturn(List.of(entry1, entry2, entry3));

      outboxRelay.processOutbox();

      verify(outboxPort).markAsProcessed(idsCaptor.capture());

      List<UUID> processedIds = idsCaptor.getValue();
      assertEquals(3, processedIds.size());
      assertEquals(entryId1, processedIds.get(0));
      assertEquals(entryId2, processedIds.get(1));
      assertEquals(entryId3, processedIds.get(2));
    }

    @Test
    @DisplayName("should continue processing other entries when one fails")
    void shouldContinueProcessingWhenOneFails() {
      UUID entryId1 = UUID.randomUUID();
      UUID entryId2 = UUID.randomUUID();

      String payload1 = "{ invalid json }";
      String payload2 =
          """
          {"aggregateId":2,"occurredAt":"2026-02-07T12:00:00Z","eventType":"USER_DELETED"}
          """;

      OutboxEntry entry1 = new OutboxEntry(entryId1, "1", "User", "USER_CREATED", payload1, 0);
      OutboxEntry entry2 = new OutboxEntry(entryId2, "2", "User", "USER_DELETED", payload2, 0);

      when(outboxPort.findUnprocessed(outboxProperties.batchSize()))
          .thenReturn(List.of(entry1, entry2));

      outboxRelay.processOutbox();

      verify(outboxPort).incrementAttempt(eq(entryId1), anyString());

      verify(outboxPort).markAsProcessed(idsCaptor.capture());

      List<UUID> processedIds = idsCaptor.getValue();

      assertEquals(1, processedIds.size());
      assertEquals(entryId2, processedIds.get(0));
    }
  }
}
