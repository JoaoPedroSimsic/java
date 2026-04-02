package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.database.retry")
public record DatabaseRetryProperties(
    @Min(1) @Min(1) int maxAttempts,
    @Min(1) long initialBackoffMs,
    @Min(1) double multiplier,
    @Min(1) long maxDelayMs) {}
