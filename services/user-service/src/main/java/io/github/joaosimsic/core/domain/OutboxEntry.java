package io.github.joaosimsic.core.domain;

import java.util.UUID;

public record OutboxEntry(
    UUID id,
    String aggregateId,
    String aggregateType,
    String eventType,
    String payload,
    int attempts) {}
