package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@Valid @NotNull Cookie cookie, @Valid @NotNull Github github) {

  public record Cookie(
      @NotBlank String domain,
      boolean secure,
      @NotBlank String sameSite,
      @Min(1) int accessTokenMaxAge,
      @Min(1) int refreshTokenMaxAge) {}

  public record Github(@NotBlank String redirectUri) {}
}
