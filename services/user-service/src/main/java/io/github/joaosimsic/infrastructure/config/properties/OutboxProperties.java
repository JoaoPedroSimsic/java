package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {
  @Min(1)
  private int batchSize;

  @Min(1)
  private int maxAttempts;

  @Min(100)
  private long pollInterval;
}
