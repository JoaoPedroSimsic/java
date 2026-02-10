package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.database.retry")
public class DatabaseRetryProperties {
  @Min(1)
  private int maxAttempts;

  @Min(1)
  private long initialBackoffMs;

  @Min(1)
  private long maxBackoffMs;
}
