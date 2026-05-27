package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

@Validated
@Profile("dev")
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
    @NotBlank String serverUrl,
    String externalUrl,
    @NotBlank String realm,
    @NotBlank String clientId,
    @Valid @NotNull Admin admin) {

  public String getExternalUrl() {
    return externalUrl != null && !externalUrl.isBlank() ? externalUrl : serverUrl;
  }

  public record Admin(@NotBlank String username, @NotBlank String password) {}
}
