package io.github.joaosimsic.infrastructure.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

@Validated
@Profile("prod")
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(
    @NotBlank String userPoolId,
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotBlank String domainUrl,
    @NotBlank String region) {}
