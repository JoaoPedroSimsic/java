package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(
    @NotBlank String userPoolId,
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotBlank String domainUrl,
    @NotBlank String region) {}
