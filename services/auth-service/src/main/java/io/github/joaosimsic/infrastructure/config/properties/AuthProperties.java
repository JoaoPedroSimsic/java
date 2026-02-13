package io.github.joaosimsic.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
  private Cookie cookie = new Cookie();
  private Github github = new Github();

  @Data
  public static class Cookie {
    private String domain = "localhost";
    private boolean secure = false;
    private String sameSite = "Lax";
    private int accessTokenMaxAge = 300;
    private int refreshTokenMaxAge = 1800;
  }

  @Data
  public static class Github {
    private String redirectUri;
  }
}
