package io.github.joaosimsic.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
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
  @Mock private OutboxPort outboxPort;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(authPort, outboxPort);
  }

  @Nested
  @DisplayName("register")
  class Register {

    @Test
    @DisplayName("should create user, save to outbox, and login in correct order")
    void shouldRegisterUserInCorrectOrder() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      AuthUser createdUser = AuthUser.builder().id("user-123").email(email).name(name).build();
      AuthTokens expectedTokens = AuthTokens.builder().accessToken("access-token").build();

      when(authPort.createUser(name, email, password)).thenReturn(createdUser);
      when(authPort.login(email, password)).thenReturn(expectedTokens);

      authService.register(name, email, password);

      InOrder inOrder = inOrder(authPort, outboxPort);
      inOrder.verify(authPort).createUser(name, email, password);
      inOrder.verify(outboxPort).save(any(UserRegisteredEvent.class), eq("user-123"), eq("AUTH"), eq("USER_REGISTERED"));
      inOrder.verify(authPort).login(email, password);
    }

    @Test
    @DisplayName("should save UserRegisteredEvent to outbox with correct data")
    void shouldSaveUserRegisteredEventToOutbox() {
      String email = "john@example.com";
      AuthUser createdUser = AuthUser.builder().id("user-123").email(email).name("John Doe").build();

      when(authPort.createUser(any(), any(), any())).thenReturn(createdUser);
      when(authPort.login(any(), any())).thenReturn(AuthTokens.builder().build());

      authService.register("John Doe", email, "password123");

      ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
      verify(outboxPort).save(eventCaptor.capture(), eq("user-123"), eq("AUTH"), eq("USER_REGISTERED"));

      UserRegisteredEvent event = eventCaptor.getValue();
      assertEquals("user-123", event.getExternalId());
      assertEquals(email, event.getEmail());
      assertNotNull(event.getOccurredAt());
    }

    @Test
    @DisplayName("should not save to outbox or login when user creation fails")
    void shouldNotProceedWhenUserCreationFails() {
      when(authPort.createUser(any(), any(), any())).thenThrow(UserAlreadyExistsException.class);

      assertThrows(UserAlreadyExistsException.class, () -> authService.register("a", "b", "c"));

      verify(outboxPort, never()).save(any(), any(), any(), any());
      verify(authPort, never()).login(any(), any());
    }
  }

  @Nested
  @DisplayName("handleGitHubCallback")
  class HandleGitHubCallback {

    @Test
    @DisplayName("should exchange code, fetch info, and save event to outbox")
    void shouldHandleGitHubFlow() {
      String code = "auth-code";
      String redirectUri = "http://localhost:3000";
      AuthTokens tokens = AuthTokens.builder().accessToken("token").idToken("id-token").build();
      AuthUser user = AuthUser.builder().id("gh-123").email("gh@test.com").name("GH User").build();

      when(authPort.exchangeCodeForTokens(code, redirectUri)).thenReturn(tokens);
      when(authPort.parseIdToken("id-token")).thenReturn(user);

      authService.handleGitHubCallback(code, redirectUri);

      ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
      verify(outboxPort).save(eventCaptor.capture(), eq("gh-123"), eq("AUTH"), eq("USER_REGISTERED"));
      
      assertEquals("gh@test.com", eventCaptor.getValue().getEmail());
    }
  }

  @Nested
  @DisplayName("updateEmail")
  class UpdateEmail {

    @Test
    @DisplayName("should update email and save EmailUpdatedEvent to outbox")
    void shouldUpdateEmailAndSaveEvent() {
      String userId = "user-123";
      String newEmail = "new@example.com";

      authService.updateEmail(userId, newEmail);

      verify(authPort).updateEmail(userId, newEmail);

      ArgumentCaptor<EmailUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(EmailUpdatedEvent.class);
      verify(outboxPort).save(eventCaptor.capture(), eq(userId), eq("AUTH"), eq("USER_EMAIL_UPDATED"));

      EmailUpdatedEvent event = eventCaptor.getValue();
      assertEquals(userId, event.getExternalId());
      assertEquals(newEmail, event.getNewEmail());
      assertEquals("USER_EMAIL_UPDATED", event.getEventType());
    }
  }

  @Nested
  @DisplayName("Standard Delegations")
  class Delegations {
    // These tests remain simple as they only verify the Port call
    
    @Test
    void loginDelegatesToPort() {
      authService.login("u", "p");
      verify(authPort).login("u", "p");
    }

    @Test
    void logoutDelegatesToPort() {
      authService.logout("token");
      verify(authPort).logout("token");
    }

    @Test
    void updatePasswordDelegatesToPort() {
      authService.updatePassword("id", "old", "new");
      verify(authPort).updatePassword("id", "old", "new");
    }
  }
}
