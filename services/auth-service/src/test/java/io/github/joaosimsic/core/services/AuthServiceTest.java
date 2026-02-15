package io.github.joaosimsic.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.core.ports.output.EventPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private AuthPort authPort;

  @Mock private EventPublisherPort eventPublisher;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(authPort, eventPublisher);
  }

  @Nested
  @DisplayName("register")
  class Register {

    @Test
    @DisplayName("should create user, publish event, and login in correct order")
    void shouldRegisterUserInCorrectOrder() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      AuthUser createdUser =
          AuthUser.builder().id("user-123").email(email).name(name).emailVerified(true).build();

      AuthTokens expectedTokens =
          AuthTokens.builder()
              .accessToken("access-token")
              .refreshToken("refresh-token")
              .idToken("id-token")
              .expiresIn(300)
              .refreshExpiresIn(1800)
              .build();

      when(authPort.createUser(name, email, password)).thenReturn(createdUser);
      when(authPort.login(email, password)).thenReturn(expectedTokens);

      AuthTokens result = authService.register(name, email, password);

      assertEquals(expectedTokens, result);

      InOrder inOrder = inOrder(authPort, eventPublisher);
      inOrder.verify(authPort).createUser(name, email, password);
      inOrder.verify(eventPublisher).publishUserRegistered(any(UserRegisteredEvent.class));
      inOrder.verify(authPort).login(email, password);
    }

    @Test
    @DisplayName("should publish UserRegisteredEvent with correct data")
    void shouldPublishUserRegisteredEventWithCorrectData() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      AuthUser createdUser =
          AuthUser.builder().id("user-123").email(email).name(name).emailVerified(true).build();

      AuthTokens tokens =
          AuthTokens.builder().accessToken("access-token").refreshToken("refresh-token").build();

      when(authPort.createUser(name, email, password)).thenReturn(createdUser);
      when(authPort.login(email, password)).thenReturn(tokens);

      authService.register(name, email, password);

      ArgumentCaptor<UserRegisteredEvent> eventCaptor =
          ArgumentCaptor.forClass(UserRegisteredEvent.class);
      verify(eventPublisher).publishUserRegistered(eventCaptor.capture());

      UserRegisteredEvent capturedEvent = eventCaptor.getValue();
      assertEquals("user-123", capturedEvent.getExternalId());
      assertEquals(email, capturedEvent.getEmail());
      assertEquals(name, capturedEvent.getName());
      assertNotNull(capturedEvent.getOccurredAt());
      assertEquals("USER_REGISTERED", capturedEvent.getEventType());
    }

    @Test
    @DisplayName("should not publish event or login when user creation fails")
    void shouldNotPublishEventOrLoginWhenUserCreationFails() {
      String name = "John Doe";
      String email = "existing@example.com";
      String password = "password123";

      when(authPort.createUser(name, email, password))
          .thenThrow(
              new UserAlreadyExistsException("User with email " + email + " already exists"));

      assertThrows(
          UserAlreadyExistsException.class, () -> authService.register(name, email, password));

      verify(eventPublisher, never()).publishUserRegistered(any(UserRegisteredEvent.class));
      verify(authPort, never()).login(any(), any());
    }
  }

  @Nested
  @DisplayName("login")
  class Login {

    @Test
    @DisplayName("should delegate to authPort and return tokens")
    void shouldDelegateToAuthPortAndReturnTokens() {
      String email = "john@example.com";
      String password = "password123";

      AuthTokens expectedTokens =
          AuthTokens.builder()
              .accessToken("access-token")
              .refreshToken("refresh-token")
              .idToken("id-token")
              .expiresIn(300)
              .refreshExpiresIn(1800)
              .build();

      when(authPort.login(email, password)).thenReturn(expectedTokens);

      AuthTokens result = authService.login(email, password);

      assertEquals(expectedTokens, result);
      verify(authPort).login(email, password);
    }
  }

  @Nested
  @DisplayName("refresh")
  class Refresh {

    @Test
    @DisplayName("should delegate to authPort and return new tokens")
    void shouldDelegateToAuthPortAndReturnNewTokens() {
      String refreshToken = "old-refresh-token";

      AuthTokens expectedTokens =
          AuthTokens.builder()
              .accessToken("new-access-token")
              .refreshToken("new-refresh-token")
              .idToken("new-id-token")
              .expiresIn(300)
              .refreshExpiresIn(1800)
              .build();

      when(authPort.refreshToken(refreshToken)).thenReturn(expectedTokens);

      AuthTokens result = authService.refresh(refreshToken);

      assertEquals(expectedTokens, result);
      verify(authPort).refreshToken(refreshToken);
    }
  }

  @Nested
  @DisplayName("logout")
  class Logout {

    @Test
    @DisplayName("should delegate to authPort")
    void shouldDelegateToAuthPort() {
      String refreshToken = "refresh-token";

      authService.logout(refreshToken);

      verify(authPort).logout(refreshToken);
    }
  }

  @Nested
  @DisplayName("getGitHubAuthUrl")
  class GetGitHubAuthUrl {

    @Test
    @DisplayName("should delegate to authPort with correct parameters")
    void shouldDelegateToAuthPortWithCorrectParameters() {
      String redirectUri = "http://localhost:3000/callback";
      String state = "random-state";
      String expectedUrl = "https://github.com/oauth/authorize?...";

      when(authPort.getGitHubAuthUrl(redirectUri, state)).thenReturn(expectedUrl);

      String result = authService.getGitHubAuthUrl(redirectUri, state);

      assertEquals(expectedUrl, result);
      verify(authPort).getGitHubAuthUrl(redirectUri, state);
    }
  }

  @Nested
  @DisplayName("handleGitHubCallback")
  class HandleGitHubCallback {

    @Test
    @DisplayName("should exchange code for tokens, fetch user info, and publish event")
    void shouldExchangeCodeFetchUserInfoAndPublishEvent() {
      String code = "auth-code";
      String redirectUri = "http://localhost:3000/callback";

      AuthTokens tokens =
          AuthTokens.builder()
              .accessToken("access-token")
              .refreshToken("refresh-token")
              .idToken("id-token")
              .expiresIn(300)
              .refreshExpiresIn(1800)
              .build();

      AuthUser user =
          AuthUser.builder()
              .id("github-user-123")
              .email("github@example.com")
              .name("GitHub User")
              .emailVerified(true)
              .build();

      when(authPort.exchangeCodeForTokens(code, redirectUri)).thenReturn(tokens);
      when(authPort.getUserInfo(tokens.getAccessToken())).thenReturn(user);

      AuthTokens result = authService.handleGitHubCallback(code, redirectUri);

      assertEquals(tokens, result);

      InOrder inOrder = inOrder(authPort, eventPublisher);
      inOrder.verify(authPort).exchangeCodeForTokens(code, redirectUri);
      inOrder.verify(authPort).getUserInfo(tokens.getAccessToken());
      inOrder.verify(eventPublisher).publishUserRegistered(any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("should publish UserRegisteredEvent with GitHub user data")
    void shouldPublishUserRegisteredEventWithGitHubUserData() {
      String code = "auth-code";
      String redirectUri = "http://localhost:3000/callback";

      AuthTokens tokens =
          AuthTokens.builder().accessToken("access-token").refreshToken("refresh-token").build();

      AuthUser user =
          AuthUser.builder()
              .id("github-user-123")
              .email("github@example.com")
              .name("GitHub User")
              .emailVerified(true)
              .build();

      when(authPort.exchangeCodeForTokens(code, redirectUri)).thenReturn(tokens);
      when(authPort.getUserInfo(tokens.getAccessToken())).thenReturn(user);

      authService.handleGitHubCallback(code, redirectUri);

      ArgumentCaptor<UserRegisteredEvent> eventCaptor =
          ArgumentCaptor.forClass(UserRegisteredEvent.class);
      verify(eventPublisher).publishUserRegistered(eventCaptor.capture());

      UserRegisteredEvent capturedEvent = eventCaptor.getValue();
      assertEquals("github-user-123", capturedEvent.getExternalId());
      assertEquals("github@example.com", capturedEvent.getEmail());
      assertEquals("GitHub User", capturedEvent.getName());
      assertNotNull(capturedEvent.getOccurredAt());
    }
  }

  @Nested
  @DisplayName("getCurrentUser")
  class GetCurrentUser {

    @Test
    @DisplayName("should delegate to authPort and return user info")
    void shouldDelegateToAuthPortAndReturnUserInfo() {
      String accessToken = "access-token";

      AuthUser expectedUser =
          AuthUser.builder()
              .id("user-123")
              .email("john@example.com")
              .name("John Doe")
              .emailVerified(true)
              .build();

      when(authPort.getUserInfo(accessToken)).thenReturn(expectedUser);

      AuthUser result = authService.getCurrentUser(accessToken);

      assertEquals(expectedUser, result);
      verify(authPort).getUserInfo(accessToken);
    }
  }

  @Nested
  @DisplayName("updateEmail")
  class UpdateEmail {

    @Test
    @DisplayName("should update email and publish event")
    void shouldUpdateEmailAndPublishEvent() {
      String userId = "user-123";
      String newEmail = "newemail@example.com";

      authService.updateEmail(userId, newEmail);

      InOrder inOrder = inOrder(authPort, eventPublisher);
      inOrder.verify(authPort).updateEmail(userId, newEmail);
      inOrder.verify(eventPublisher).publishUserEmailUpdated(any(EmailUpdatedEvent.class));
    }

    @Test
    @DisplayName("should publish UserEmailUpdatedEvent with correct data")
    void shouldPublishUserEmailUpdatedEventWithCorrectData() {
      String userId = "user-123";
      String newEmail = "newemail@example.com";

      authService.updateEmail(userId, newEmail);

      ArgumentCaptor<EmailUpdatedEvent> eventCaptor =
          ArgumentCaptor.forClass(EmailUpdatedEvent.class);
      verify(eventPublisher).publishUserEmailUpdated(eventCaptor.capture());

      EmailUpdatedEvent capturedEvent = eventCaptor.getValue();
      assertEquals(userId, capturedEvent.getExternalId());
      assertEquals(newEmail, capturedEvent.getNewEmail());
      assertNotNull(capturedEvent.getOccurredAt());
      assertEquals("USER_EMAIL_UPDATED", capturedEvent.getEventType());
    }
  }

  @Nested
  @DisplayName("updatePassword")
  class UpdatePassword {

    @Test
    @DisplayName("should delegate to authPort")
    void shouldDelegateToAuthPort() {
      String userId = "user-123";
      String currentPassword = "oldPassword123";
      String newPassword = "newPassword456";

      authService.updatePassword(userId, currentPassword, newPassword);

      verify(authPort).updatePassword(userId, currentPassword, newPassword);
    }
  }
}
