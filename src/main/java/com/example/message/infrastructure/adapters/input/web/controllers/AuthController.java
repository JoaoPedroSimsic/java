package com.example.message.infrastructure.adapters.input.web.controllers;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.InvalidCredentialsException;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.TokenRepositoryPort;
import com.example.message.infrastructure.adapters.input.web.requests.LoginRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
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
  public ResponseEntity<String> login(
      @RequestBody LoginRequest request, HttpServletResponse response) {
    User user = userUseCase.findByEmail(request.email());

    if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new InvalidCredentialsException("Email or Password Incorrect");
    }

    String token = tokenRepositoryPort.generateToken(user);

    Cookie cookie = new Cookie("jwt", token);

    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge(86400);

    response.addCookie(cookie);

    return ResponseEntity.ok().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("jwt", null);

    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);

    response.addCookie(cookie);

    return ResponseEntity.noContent().build();
  }
}
