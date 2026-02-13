package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.infrastructure.config.properties.KeycloakProperties;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class KeycloakConfig {

  @Bean
  Keycloak keycloakAdminClient(KeycloakProperties properties) {
    return KeycloakBuilder.builder()
        .serverUrl(properties.getServerUrl())
        .realm("master")
        .clientId("admin-cli")
        .username(properties.getAdmin().getUsername())
        .password(properties.getAdmin().getPassword())
        .build();
  }
}
