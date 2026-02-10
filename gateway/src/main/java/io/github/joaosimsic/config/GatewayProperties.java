package io.github.joaosimsic.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
    @Valid @NotNull JwtConfig jwt,
    @Valid @NotNull RateLimitConfig rateLimit,
    @NotBlank String secret) {
  public record JwtConfig(
      @NotBlank String jwksUrl, @Min(1) long cacheTtlSeconds, @NotBlank String expectedIssuer) {}

  public record RateLimitConfig(
      @Valid @NotBlank RateDetails authenticated, @Valid @NotNull RateDetails unauthenticated) {

    public RateLimitConfig {
      if (authenticated == null) authenticated = new RateDetails(100, 150);
      if (unauthenticated == null) unauthenticated = new RateDetails(5, 10);
    }
  }

  public record RateDetails(@Min(1) int replenishRate, @Min(1) int burstCapacity) {}
}
