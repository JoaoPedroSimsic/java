package com.example.message.infrastructure.config;

import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.OutboxPort;
import com.example.message.core.ports.output.UserPort;
import com.example.message.core.services.UserService;
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
