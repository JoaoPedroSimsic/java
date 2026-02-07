package com.example.message.core.domain;

import java.util.UUID;

public record OutboxEntry(
    UUID id, String aggregateId, String aggregateType, String eventType, String payload) {}
