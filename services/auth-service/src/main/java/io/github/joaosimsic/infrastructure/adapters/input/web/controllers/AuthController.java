package io.github.joaosimsic.infrastructure.adapters.input.web.controllers;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.ports.input.AuthUseCase;
import io.github.joaosimsic.infrastructure.adapters.input.web.dto.request.AuthRequest;
import io.github.joaosimsic.infrastructure.adapters.input.web.dto.request.LoginRequest;
import io.github.joaosimsic.infrastructure.adapters.input.web.dto.response.AuthResponse;
import io.github.joaosimsic.infrastructure.adapters.input.web.dto.response.GitHubAuthUrlResponse;
import io.github.joaosimsic.infrastructure.adapters.input.web.dto.response.UserResponse;
import io.github.joaosimsic.infrastructure.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthUseCase authUseCase;
  private final AuthProperties authProperties;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody AuthRequest request, HttpServletResponse response) {

    AuthTokens tokens =
        authUseCase.register(request.getName(), request.getEmail(), request.getPassword());

    setAuthCookies(response, tokens);

    AuthUser user = authUseCase.getCurrentUser(tokens.getAccessToken());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            AuthResponse.builder()
                .message("Registration successful")
                .user(mapToUserResponse(user))
                .build());
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {

    AuthTokens tokens = authUseCase.login(request.getEmail(), request.getPassword());

    setAuthCookies(response, tokens);

    AuthUser user = authUseCase.getCurrentUser(tokens.getAccessToken());

    return ResponseEntity.ok(
        AuthResponse.builder().message("Login successful").user(mapToUserResponse(user)).build());
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response) {

    if (refreshToken != null) {
      authUseCase.logout(refreshToken);
    }

    clearAuthCookies(response);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = "refresh_token") String refreshToken, HttpServletResponse response) {

    AuthTokens tokens = authUseCase.refresh(refreshToken);

    setAuthCookies(response, tokens);

    AuthUser user = authUseCase.getCurrentUser(tokens.getAccessToken());

    return ResponseEntity.ok(
        AuthResponse.builder().message("Token refreshed").user(mapToUserResponse(user)).build());
  }

  @GetMapping("/github")
  public ResponseEntity<GitHubAuthUrlResponse> getGitHubAuthUrl(@RequestParam String redirectUri) {

    String state = UUID.randomUUID().toString();
    String authUrl = authUseCase.getGitHubAuthUrl(redirectUri, state);

    return ResponseEntity.ok(new GitHubAuthUrlResponse(authUrl, state));
  }

  @GetMapping("/github/callback")
  public ResponseEntity<AuthResponse> handleGitHubCallback(
      @RequestParam String code, @RequestParam String redirectUri, HttpServletResponse response) {

    AuthTokens tokens = authUseCase.handleGitHubCallback(code, redirectUri);

    setAuthCookies(response, tokens);

    AuthUser user = authUseCase.getCurrentUser(tokens.getAccessToken());

    return ResponseEntity.ok(
        AuthResponse.builder()
            .message("GitHub login successful")
            .user(mapToUserResponse(user))
            .build());
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponse> getCurrentUser(
      @CookieValue(name = "access_token") String accessToken) {

    AuthUser user = authUseCase.getCurrentUser(accessToken);
    return ResponseEntity.ok(mapToUserResponse(user));
  }

  private void setAuthCookies(HttpServletResponse response, AuthTokens tokens) {
    Cookie accessTokenCookie = new Cookie("access_token", tokens.getAccessToken());
    accessTokenCookie.setHttpOnly(true);
    accessTokenCookie.setSecure(authProperties.getCookie().isSecure());
    accessTokenCookie.setPath("/");
    accessTokenCookie.setMaxAge(tokens.getExpiresIn());
    accessTokenCookie.setAttribute("SameSite", authProperties.getCookie().getSameSite());
    response.addCookie(accessTokenCookie);

    Cookie refreshTokenCookie = new Cookie("refresh_token", tokens.getRefreshToken());
    refreshTokenCookie.setHttpOnly(true);
    refreshTokenCookie.setSecure(authProperties.getCookie().isSecure());
    refreshTokenCookie.setPath("/api/auth");
    refreshTokenCookie.setMaxAge(tokens.getRefreshExpiresIn());
    refreshTokenCookie.setAttribute("SameSite", authProperties.getCookie().getSameSite());
    response.addCookie(refreshTokenCookie);
  }

  private void clearAuthCookies(HttpServletResponse response) {
    Cookie accessTokenCookie = new Cookie("access_token", "");
    accessTokenCookie.setHttpOnly(true);
    accessTokenCookie.setSecure(authProperties.getCookie().isSecure());
    accessTokenCookie.setPath("/");
    accessTokenCookie.setMaxAge(0);
    response.addCookie(accessTokenCookie);

    Cookie refreshTokenCookie = new Cookie("refresh_token", "");
    refreshTokenCookie.setHttpOnly(true);
    refreshTokenCookie.setSecure(authProperties.getCookie().isSecure());
    refreshTokenCookie.setPath("/api/auth");
    refreshTokenCookie.setMaxAge(0);
    response.addCookie(refreshTokenCookie);
  }

  private UserResponse mapToUserResponse(AuthUser user) {
    return UserResponse.builder()
        .id(user.getId())
        .email(user.getEmail())
        .name(user.getName())
        .emailVerified(user.isEmailVerified())
        .build();
  }
}
