  package io.github.joaosimsic.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.database.retry")
public class DatabaseRetryProperties {
  private int maxAttempts = 5;
  private long initialBackoffMs = 2000;
  private long maxBackoffMs = 10000;
}
