package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.infrastructure.config.properties.DatabaseRetryProperties;
import io.github.joaosimsic.infrastructure.config.properties.JwtProperties;
import io.github.joaosimsic.infrastructure.config.properties.OutboxProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ValidationConfig {
  private final OutboxProperties outboxProperties;
  private final JwtProperties jwtProperties;
  private final DatabaseRetryProperties databaseRetryProperties;

  @PostConstruct
  public void init() {
    String secret = jwtProperties.secret();
    String maskSecret = maskSecret(secret);

    log.info("Validating infrastructure configuration for User Service...");

    log.info(
        "[OK] Outbox Config -> Batch Size: {}, Max Attempts: {}, Poll Interval: {}ms",
        outboxProperties.batchSize(),
        outboxProperties.maxAttempts(),
        outboxProperties.pollInterval());

    log.info("[OK] JWT -> Secret: {}", maskSecret);

    log.info(
        "Database Retry Config -> Max Attempts: {}, Initial Interval: {}ms",
        databaseRetryProperties.maxAttempts(),
        databaseRetryProperties.initialBackoffMs());

    log.info("Infrastructure configuration validated successfully.");
  }

  private String maskSecret(String secret) {
    if (secret == null || secret.isBlank()) {
      return "NOT_SET";
    }
    if (secret.length() <= 8) {
      return "********";
    }
    return secret.substring(0, 4) + "...." + secret.substring(secret.length() - 4);
  }
}
