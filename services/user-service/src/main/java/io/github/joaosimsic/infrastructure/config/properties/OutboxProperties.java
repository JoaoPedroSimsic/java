package io.github.joaosimsic.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "spring.outbox")
public class OutboxProperties {
  @Min(1)
  private int batchSize = 20;

  @Min(1)
  private int maxAttempts = 5;

  @Min(100)
  private long poolInterval = 5000;
}
