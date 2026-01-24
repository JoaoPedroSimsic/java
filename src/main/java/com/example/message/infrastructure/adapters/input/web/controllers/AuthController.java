package com.example.message.infrastructure.adapters.input.web.controllers;

import com.example.message.core.domain.User;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.TokenRepositoryPort;
import com.example.message.infrastructure.adapters.input.web.requests.LoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserUseCase userUseCase;
  private final TokenRepositoryPort tokenRepositoryPort;
  private final PasswordEncoder passwordEncoder;

  public AuthController(
      UserUseCase userUseCase,
      TokenRepositoryPort tokenRepositoryPort,
      PasswordEncoder passwordEncoder) {
    this.userUseCase = userUseCase;
    this.tokenRepositoryPort = tokenRepositoryPort;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping("/login")
  public ResponseEntity<String> login(@RequestBody LoginRequest request) {
    User user = userUseCase.findByEmail(request.email());

    if (passwordEncoder.matches(request.password(), user.getPassword())) {
      return ResponseEntity.ok(tokenRepositoryPort.generateToken(user));
    }

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
}
