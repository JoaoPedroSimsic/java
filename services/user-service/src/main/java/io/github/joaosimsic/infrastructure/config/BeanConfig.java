package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.core.ports.input.UserUseCase;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.core.ports.output.UserPort;
import io.github.joaosimsic.core.services.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BeanConfig {
  @Bean
  public UserUseCase userUseCase(
      UserPort userRepositoryPort, PasswordEncoder passwordEncoder, OutboxPort outboxPort) {
    return new UserService(userRepositoryPort, passwordEncoder, outboxPort);
  }
}
