package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.core.ports.input.UserUseCase;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.core.ports.output.UserPort;
import io.github.joaosimsic.core.services.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {
  @Bean
  UserUseCase userUseCase(UserPort userRepositoryPort, OutboxPort outboxPort) {
    return new UserService(userRepositoryPort, outboxPort);
  }
}
