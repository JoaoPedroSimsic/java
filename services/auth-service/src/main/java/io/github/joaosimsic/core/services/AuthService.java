package io.github.joaosimsic.core.services;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.domain.GitHubAuthResult;
import io.github.joaosimsic.core.ports.input.AuthUseCase;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

  private final AuthPort authPort;
  private final OutboxPort outboxPort;

  @Override
  public AuthTokens register(String name, String email, String password) {
    log.info("Registering new user with email: {}", email);

    AuthUser user = authPort.createUser(name, email, password);

    var event =
        new UserRegisteredEvent()
            .withExternalId(user.getId())
            .withEmail(email)
            .withName(name)
            .withOccurredAt(Instant.now())
            .withEventType("USER_REGISTERED")
            .withTraceId(MDC.get("traceId"));

    outboxPort.save(event, String.valueOf(user.getId()), "AUTH", event.getEventType());

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
  public GitHubAuthResult handleGitHubCallback(String code, String redirectUri) {
    log.info("Handling GitHub OAuth callback");

    AuthTokens tokens = authPort.exchangeCodeForTokens(code, redirectUri);

    // Parse user info from ID token instead of calling userinfo endpoint
    AuthUser user = authPort.parseIdToken(tokens.getIdToken());

    var event =
        new UserRegisteredEvent()
            .withExternalId(user.getId())
            .withEmail(user.getEmail())
            .withName(user.getName())
            .withOccurredAt(Instant.now())
            .withEventType("USER_REGISTERED")
            .withTraceId(MDC.get("traceId"));

    outboxPort.save(event, String.valueOf(user.getId()), "AUTH", event.getEventType());

    return new GitHubAuthResult(tokens, user);
  }

  @Override
  public AuthUser getCurrentUser(String accessToken) {
    log.debug("Fetching current user info");

    return authPort.getUserInfo(accessToken);
  }

  @Override
  public void updateEmail(String userId, String newEmail) {
    log.info("Updating email for user: {}", userId);

    authPort.updateEmail(userId, newEmail);

    var event =
        new EmailUpdatedEvent()
            .withExternalId(userId)
            .withNewEmail(newEmail)
            .withOccurredAt(Instant.now())
            .withEventType("USER_EMAIL_UPDATED")
            .withTraceId(MDC.get("traceId"));

    outboxPort.save(event, String.valueOf(userId), "AUTH", event.getEventType());

    log.info("Email updated successfully for user: {}", userId);
  }

  @Override
  public void updatePassword(String userId, String currentPassword, String newPassword) {
    log.info("Updating password for user: {}", userId);

    authPort.updatePassword(userId, currentPassword, newPassword);

    log.info("Password updated successfully for user: {}", userId);
  }
}
