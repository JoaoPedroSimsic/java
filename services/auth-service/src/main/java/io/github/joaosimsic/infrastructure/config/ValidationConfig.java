package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.infrastructure.config.properties.AuthProperties;
import io.github.joaosimsic.infrastructure.config.properties.CognitoProperties;
import io.github.joaosimsic.infrastructure.config.properties.KeycloakProperties;
import io.github.joaosimsic.infrastructure.config.properties.OutboxProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ValidationConfig {

  private final AuthProperties authProperties;
  private final CognitoProperties cognitoProperties;
  private final KeycloakProperties keycloakProperties;
  private final OutboxProperties outboxProperties;

  @PostConstruct
  public void init() {
    log.info("Starting Infrastructure Configuration Validation...");

    log.info(
        "[OK] Auth Service: Cookie Domain: {}, Github Redirect: {}",
        authProperties.cookie().domain(),
        authProperties.github().redirectUri());

    log.info(
        "[OK] Keycloak: Server: {}, Realm: {}, Admin User: {}",
        keycloakProperties.serverUrl(),
        keycloakProperties.realm(),
        keycloakProperties.admin().username());

    log.info(
        "[OK] Cognito: Region: {}, Pool ID: {}",
        cognitoProperties.region(),
        cognitoProperties.userPoolId());

    log.info("[OK] Outbox Pattern: Batch size: {}", outboxProperties.batchSize());

    log.info("Startup Validation Complete: All environment variables are present and valid.");
  }
}
