package com.example.message.infrastructure.config;

import com.example.message.core.ports.input.AuthUseCase;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.SessionRepositoryPort;
import com.example.message.core.ports.output.UserRepositoryPort;
import com.example.message.core.services.AuthService;
import com.example.message.core.services.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class BeanConfiguration {

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserUseCase userUseCase(
      UserRepositoryPort userRepositoryPort, BCryptPasswordEncoder passwordEncoder) {
    return new UserService(userRepositoryPort, passwordEncoder);
  }

  @Bean
  public AuthUseCase authUseCase(
      UserRepositoryPort userRepositoryPort,
      SessionRepositoryPort sessionRepositoryPort,
      BCryptPasswordEncoder passwordEncoder,
      @Value("${auth.session.timeout-hours:24}") int sessionTimeoutHours) {
    return new AuthService(
        userRepositoryPort, sessionRepositoryPort,
        passwordEncoder, sessionTimeoutHours);
  }
}
