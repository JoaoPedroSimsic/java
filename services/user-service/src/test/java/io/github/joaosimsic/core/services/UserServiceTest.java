package io.github.joaosimsic.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.events.user.UserCreatedEvent;
import io.github.joaosimsic.events.user.UserDeletedEvent;
import io.github.joaosimsic.events.user.UserUpdatedEvent;
import io.github.joaosimsic.core.exceptions.business.*;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.core.ports.output.UserPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserPort userRepositoryPort;

  @Mock private OutboxPort outboxPort;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepositoryPort, outboxPort);
  }

  @Nested
  @DisplayName("createUser")
  class CreateUser {

    @Test
    @DisplayName("should create user when email does not exist")
    void shouldCreateUser() {
      User user = User.builder().name("John Doe").email("john@example.com").build();

      User savedUser = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(null);
      when(userRepositoryPort.save(any(User.class))).thenReturn(savedUser);

      User result = userService.createUser(user);

      assertEquals(1L, result.getId());
      assertEquals("John Doe", result.getName());
      assertEquals("john@example.com", result.getEmail());

      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepositoryPort).save(userCaptor.capture());
    }

    @Test
    @DisplayName("should save UserCreatedEvent to outbox when user is created")
    void shouldSaveUserCreatedEventToOutbox() {
      User user = User.builder().name("John Doe").email("john@example.com").build();

      User savedUser = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(null);
      when(userRepositoryPort.save(any(User.class))).thenReturn(savedUser);

      userService.createUser(user);

      ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
      verify(outboxPort).save(eventCaptor.capture());

       DomainEvent capturedEvent = eventCaptor.getValue();
       assertInstanceOf(UserCreatedEventWrapper.class, capturedEvent);

       UserCreatedEventWrapper event = (UserCreatedEventWrapper) capturedEvent;
       assertEquals(1L, event.aggregateId()); // Use aggregateId() from DomainEvent
       assertEquals("USER_CREATED", event.eventType()); // Use eventType() from DomainEvent
       assertNotNull(event.occurredAt()); // Use occurredAt() from DomainEvent
       assertEquals(1, event.getUserCreatedEvent().getUserId()); // Access generated event directly
       assertEquals("john@example.com", event.getUserCreatedEvent().getEmail());
       assertEquals("John Doe", event.getUserCreatedEvent().getName());
    }

    @Test
    @DisplayName("should not save event to outbox when email already exists")
    void shouldNotSaveEventWhenEmailExists() {
      User user = User.builder().name("John Doe").email("john@example.com").build();

      User existingUser = User.builder().id(1L).email("john@example.com").build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(existingUser);

      ConflictException exception =
          assertThrows(ConflictException.class, () -> userService.createUser(user));
      assertTrue(exception.getMessage().contains("john@example.com"));
      assertTrue(exception.getMessage().contains("already exists"));

      verify(userRepositoryPort, never()).save(any(User.class));
      verify(outboxPort, never()).save(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("listUsers")
  class ListUsers {

    @Test
    @DisplayName("should return all users")
    void shouldReturnAllUsers() {
      List<User> users =
          List.of(
              User.builder().id(1L).name("John").email("john@example.com").build(),
              User.builder().id(2L).name("Jane").email("jane@example.com").build());

      when(userRepositoryPort.findAll()).thenReturn(users);

      List<User> result = userService.listUsers();

      assertEquals(2, result.size());
      assertEquals("John", result.get(0).getName());
      assertEquals("Jane", result.get(1).getName());
    }

    @Test
    @DisplayName("should return empty list when no users exist")
    void shouldReturnEmptyList() {
      when(userRepositoryPort.findAll()).thenReturn(List.of());

      List<User> result = userService.listUsers();

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    @DisplayName("should return user when found")
    void shouldReturnUser() {
      User user = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      when(userRepositoryPort.find(1L)).thenReturn(user);

      User result = userService.findById(1L);

      assertEquals(1L, result.getId());
      assertEquals("John Doe", result.getName());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      when(userRepositoryPort.find(1L)).thenReturn(null);

      UserNotFoundException exception =
          assertThrows(UserNotFoundException.class, () -> userService.findById(1L));
      assertTrue(exception.getMessage().contains("1"));
    }
  }

  @Nested
  @DisplayName("findByEmail")
  class FindByEmail {

    @Test
    @DisplayName("should return user when found")
    void shouldReturnUser() {
      User user = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(user);

      User result = userService.findByEmail("john@example.com");

      assertEquals(1L, result.getId());
      assertEquals("john@example.com", result.getEmail());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(null);

      UserNotFoundException exception =
          assertThrows(
              UserNotFoundException.class, () -> userService.findByEmail("john@example.com"));
      assertTrue(exception.getMessage().contains("john@example.com"));
    }
  }

  @Nested
  @DisplayName("updateUser")
  class UpdateUser {

    @Test
    @DisplayName("should update user name")
    void shouldUpdateUserName() {
      User existingUser = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      User updateRequest =
          User.builder().id(1L).name("John Updated").email("john@example.com").build();

      User updatedUser =
          User.builder().id(1L).name("John Updated").email("john@example.com").build();

      when(userRepositoryPort.find(1L)).thenReturn(existingUser);
      when(userRepositoryPort.save(any(User.class))).thenReturn(updatedUser);

      User result = userService.updateUser(updateRequest);

      assertEquals("John Updated", result.getName());
      assertEquals("john@example.com", result.getEmail());

      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepositoryPort).save(userCaptor.capture());
      assertEquals("John Updated", userCaptor.getValue().getName());

      ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
      verify(outboxPort).save(eventCaptor.capture());

      UserCreatedEvent capturedEvent = eventCaptor.getValue();

      assertNotNull(capturedEvent);
      assertEquals(1L, capturedEvent.aggregateId());
      assertNotNull(capturedEvent.occurredAt());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      User updateRequest =
          User.builder().id(1L).name("John Updated").email("john@example.com").build();

      when(userRepositoryPort.find(1L)).thenReturn(null);

      UserNotFoundException exception =
          assertThrows(UserNotFoundException.class, () -> userService.updateUser(updateRequest));
      assertTrue(exception.getMessage().contains("1"));

      verify(userRepositoryPort, never()).save(any(User.class));
      verify(outboxPort, never()).save(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("deleteUser")
  class DeleteUser {

    @Test
    @DisplayName("should delete user when found")
    void shouldDeleteUser() {
      User user = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      when(userRepositoryPort.find(1L)).thenReturn(user);

      userService.deleteUser(1L);

      verify(userRepositoryPort).delete(1L);
    }

    @Test
    @DisplayName("should save UserDeletedEvent to outbox when user is deleted")
    void shouldSaveUserDeletedEventToOutbox() {
      User user = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      when(userRepositoryPort.find(1L)).thenReturn(user);

      userService.deleteUser(1L);

      ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
      verify(outboxPort).save(eventCaptor.capture());

       DomainEvent capturedEvent = eventCaptor.getValue();
       assertInstanceOf(UserDeletedEventWrapper.class, capturedEvent);

       UserDeletedEventWrapper event = (UserDeletedEventWrapper) capturedEvent;
       assertEquals(1L, event.aggregateId());
       assertEquals("USER_DELETED", event.eventType());
       assertNotNull(event.occurredAt());
       assertEquals(1, event.getUserDeletedEvent().getUserId());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      when(userRepositoryPort.find(1L)).thenReturn(null);

      UserNotFoundException exception =
          assertThrows(UserNotFoundException.class, () -> userService.deleteUser(1L));
      assertTrue(exception.getMessage().contains("1"));

      verify(userRepositoryPort, never()).delete(any());
      verify(outboxPort, never()).save(any(DomainEvent.class));
    }
  }
}
