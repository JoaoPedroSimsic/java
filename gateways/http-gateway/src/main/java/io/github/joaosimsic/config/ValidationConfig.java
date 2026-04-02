package io.github.joaosimsic.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ValidationConfig {

  private final GatewayProperties props;

  @PostConstruct
  public void init() {
    log.info("Gateway configuration loaded for issuer: {}", props.jwt().expectedIssuer());
  }
}
