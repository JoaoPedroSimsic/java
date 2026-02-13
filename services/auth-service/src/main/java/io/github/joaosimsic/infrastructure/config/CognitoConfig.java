package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.infrastructure.config.properties.CognitoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
@Profile("prod")
@RequiredArgsConstructor
public class CognitoConfig {

  private final CognitoProperties cognitoProperties;

  @Bean
  CognitoIdentityProviderClient cognitoIdentityProviderClient() {
    return CognitoIdentityProviderClient.builder()
        .region(Region.of(cognitoProperties.getRegion()))
        .build();
  }
}
