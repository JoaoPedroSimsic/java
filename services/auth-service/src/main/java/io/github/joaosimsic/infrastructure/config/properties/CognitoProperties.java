package io.github.joaosimsic.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cognito")
public class CognitoProperties {
  private String userPoolId;
  private String clientId;
  private String clientSecret;
  private String domainUrl;
  private String region;
}
