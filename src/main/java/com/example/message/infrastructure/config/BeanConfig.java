package com.example.message.infrastructure.config;

import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.UserRepositoryPort;
import com.example.message.core.services.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {
  @Bean
  public UserUseCase userUseCase(UserRepositoryPort userRepositoryPort) {
    return new UserService(userRepositoryPort);
  }
}
