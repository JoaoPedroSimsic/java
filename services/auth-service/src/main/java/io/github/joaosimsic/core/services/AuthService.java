package io.github.joaosimsic.core.services;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.events.UserRegisteredEvent;
import io.github.joaosimsic.core.ports.input.AuthUseCase;
import io.github.joaosimsic.core.ports.output.EventPublisherPort;
import io.github.joaosimsic.core.ports.output.AuthPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

  private final AuthPort authPort;
  private final EventPublisherPort eventPublisher;

  @Override
  public AuthTokens register(String name, String email, String password) {
    log.info("Registering new user with email: {}", email);

    AuthUser user = authPort.createUser(name, email, password);

    eventPublisher.publishUserRegistered(new UserRegisteredEvent(user.getId(), email, name));

    log.info("User registered successfully, logging in: {}", email);

    return authPort.login(email, password);
  }

  @Override
  public AuthTokens login(String email, String password) {
    log.info("Attempting login for user: {}", email);

    return authPort.login(email, password);
  }

  @Override
  public AuthTokens refresh(String refreshToken) {
    log.debug("Refreshing access token");

    return authPort.refreshToken(refreshToken);
  }

  @Override
  public void logout(String refreshToken) {
    log.info("Logging out user");

    authPort.logout(refreshToken);
  }

  @Override
  public String getGitHubAuthUrl(String redirectUri, String state) {
    log.debug("Generating GitHub auth URL");

    return authPort.getGitHubAuthUrl(redirectUri, state);
  }

  @Override
  public AuthTokens handleGitHubCallback(String code, String redirectUri) {
    log.info("Handling GitHub OAuth callback");

    AuthTokens tokens = authPort.exchangeCodeForTokens(code, redirectUri);

    AuthUser user = authPort.getUserInfo(tokens.getAccessToken());

    eventPublisher.publishUserRegistered(
        new UserRegisteredEvent(user.getId(), user.getEmail(), user.getName()));

    return tokens;
  }

  @Override
  public AuthUser getCurrentUser(String accessToken) {
    log.debug("Fetching current user info");

    return authPort.getUserInfo(accessToken);
  }
}
