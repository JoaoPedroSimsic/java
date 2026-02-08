package io.github.joaosimsic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
  private JwtConfig jwt = new JwtConfig();
  private RateLimitConfig rateLimit = new RateLimitConfig();

  @Data
  public static class JwtConfig {
    private String jwksUrl = "http://user-service:8081/.well-known/jwks.json";
    private long cacheTtlSeconds = 900;
    private String expectedIssuer = "hermes-user-service";
  }

  @Data
  public static class RateLimitConfig {
    private int replenishRate = 10;
    private int burstCapacity = 20;
  }
}
