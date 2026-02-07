package com.example.message.infrastructure.adapters.input.web.controllers;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.business.InvalidCredentialsException;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.TokenPort;
import com.example.message.infrastructure.adapters.input.web.requests.LoginRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
  private final UserUseCase userUseCase;
  private final TokenPort tokenRepositoryPort;
  private final PasswordEncoder passwordEncoder;

  public AuthController(
      UserUseCase userUseCase,
      TokenPort tokenRepositoryPort,
      PasswordEncoder passwordEncoder) {
    this.userUseCase = userUseCase;
    this.tokenRepositoryPort = tokenRepositoryPort;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping("/login")
  public ResponseEntity<String> login(
      @RequestBody LoginRequest request, HttpServletResponse response) {
    log.debug("Login attempt for email: {}", request.email());

    User user = userUseCase.findByEmail(request.email());

    if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
      log.warn("Failed login attempt for email: {}", request.email());
      throw new InvalidCredentialsException("Email or Password Incorrect");
    }

    String token = tokenRepositoryPort.generateToken(user);

    Cookie cookie = new Cookie("jwt", token);

    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge(86400);

    response.addCookie(cookie);

    log.info("Successful login for user: {}", user.getId());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    log.debug("Logout request received");

    Cookie cookie = new Cookie("jwt", null);

    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);

    response.addCookie(cookie);

    return ResponseEntity.noContent().build();
  }
}
