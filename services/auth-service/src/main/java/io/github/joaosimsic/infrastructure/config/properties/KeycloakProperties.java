package io.github.joaosimsic.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
  private String serverUrl;
  private String realm;
  private String clientId;
  private Admin admin = new Admin();

  @Data
  public static class Admin {
    private String username;
    private String password;
  }
}
