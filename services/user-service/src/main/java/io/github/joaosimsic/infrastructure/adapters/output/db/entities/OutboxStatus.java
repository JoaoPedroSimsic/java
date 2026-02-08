package io.github.joaosimsic.infrastructure.adapters.output.db.entities;

public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}

