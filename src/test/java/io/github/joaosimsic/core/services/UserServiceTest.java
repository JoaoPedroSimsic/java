package io.github.joaosimsic.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.core.exceptions.business.*;
import io.github.joaosimsic.core.ports.output.UserPort;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserPort userRepositoryPort;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock 
  private OutboxPort outboxPort;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepositoryPort, passwordEncoder, outboxPort);
  }

  @Nested
  @DisplayName("createUser")
  class CreateUser {

    @Test
    @DisplayName("should create user when email does not exist")
    void shouldCreateUser() {
      User user = User.builder()
          .name("John Doe")
          .email("john@example.com")
          .password("password123")
          .build();

      User savedUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .password("hashedPassword")
          .build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(null);
      when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
      when(userRepositoryPort.save(any(User.class))).thenReturn(savedUser);

      User result = userService.createUser(user);

      assertEquals(1L, result.getId());
      assertEquals("John Doe", result.getName());
      assertEquals("john@example.com", result.getEmail());

      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepositoryPort).save(userCaptor.capture());
      assertEquals("hashedPassword", userCaptor.getValue().getPassword());
    }

    @Test
    @DisplayName("should throw ConflictException when email already exists")
    void shouldThrowConflictExceptionWhenEmailExists() {
      User user = User.builder()
          .name("John Doe")
          .email("john@example.com")
          .password("password123")
          .build();

      User existingUser = User.builder()
          .id(1L)
          .email("john@example.com")
          .build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(existingUser);

      ConflictException exception = assertThrows(ConflictException.class,
          () -> userService.createUser(user));
      assertTrue(exception.getMessage().contains("john@example.com"));
      assertTrue(exception.getMessage().contains("already exists"));

      verify(userRepositoryPort, never()).save(any(User.class));
      verify(passwordEncoder, never()).encode(anyString());
    }
  }

  @Nested
  @DisplayName("listUsers")
  class ListUsers {

    @Test
    @DisplayName("should return all users")
    void shouldReturnAllUsers() {
      List<User> users = List.of(
          User.builder().id(1L).name("John").email("john@example.com").build(),
          User.builder().id(2L).name("Jane").email("jane@example.com").build()
      );

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
      User user = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(user);

      User result = userService.findById(1L);

      assertEquals(1L, result.getId());
      assertEquals("John Doe", result.getName());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      when(userRepositoryPort.find(1L)).thenReturn(null);

      UserNotFoundException exception = assertThrows(UserNotFoundException.class,
          () -> userService.findById(1L));
      assertTrue(exception.getMessage().contains("1"));
    }
  }

  @Nested
  @DisplayName("findByEmail")
  class FindByEmail {

    @Test
    @DisplayName("should return user when found")
    void shouldReturnUser() {
      User user = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .build();

      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(user);

      User result = userService.findByEmail("john@example.com");

      assertEquals(1L, result.getId());
      assertEquals("john@example.com", result.getEmail());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(null);

      UserNotFoundException exception = assertThrows(UserNotFoundException.class,
          () -> userService.findByEmail("john@example.com"));
      assertTrue(exception.getMessage().contains("john@example.com"));
    }
  }

  @Nested
  @DisplayName("updateUser")
  class UpdateUser {

    @Test
    @DisplayName("should update user when found and email is unique")
    void shouldUpdateUser() {
      User existingUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .password("oldHashedPassword")
          .build();

      User updateRequest = User.builder()
          .id(1L)
          .name("John Updated")
          .email("john.updated@example.com")
          .password("newPassword")
          .build();

      User savedUser = User.builder()
          .id(1L)
          .name("John Updated")
          .email("john.updated@example.com")
          .password("newHashedPassword")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(existingUser);
      when(userRepositoryPort.findByEmail("john.updated@example.com")).thenReturn(null);
      when(passwordEncoder.encode("newPassword")).thenReturn("newHashedPassword");
      when(userRepositoryPort.save(any(User.class))).thenReturn(savedUser);

      User result = userService.updateUser(updateRequest);

      assertEquals("John Updated", result.getName());
      assertEquals("john.updated@example.com", result.getEmail());
      verify(passwordEncoder).encode("newPassword");
    }

    @Test
    @DisplayName("should update user keeping same email")
    void shouldUpdateUserKeepingSameEmail() {
      User existingUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .password("oldHashedPassword")
          .build();

      User updateRequest = User.builder()
          .id(1L)
          .name("John Updated")
          .email("john@example.com")
          .password("newPassword")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(existingUser);
      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(existingUser);
      when(passwordEncoder.encode("newPassword")).thenReturn("newHashedPassword");
      when(userRepositoryPort.save(any(User.class))).thenReturn(existingUser);

      User result = userService.updateUser(updateRequest);

      assertNotNull(result);
      verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    @DisplayName("should update user without changing password when password is null")
    void shouldUpdateUserWithoutChangingPasswordWhenNull() {
      User existingUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .password("oldHashedPassword")
          .build();

      User updateRequest = User.builder()
          .id(1L)
          .name("John Updated")
          .email("john@example.com")
          .password(null)
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(existingUser);
      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(existingUser);
      when(userRepositoryPort.save(any(User.class))).thenReturn(existingUser);

      userService.updateUser(updateRequest);

      verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("should update user without changing password when password is empty")
    void shouldUpdateUserWithoutChangingPasswordWhenEmpty() {
      User existingUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .password("oldHashedPassword")
          .build();

      User updateRequest = User.builder()
          .id(1L)
          .name("John Updated")
          .email("john@example.com")
          .password("")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(existingUser);
      when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(existingUser);
      when(userRepositoryPort.save(any(User.class))).thenReturn(existingUser);

      userService.updateUser(updateRequest);

      verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      User updateRequest = User.builder()
          .id(1L)
          .name("John Updated")
          .email("john@example.com")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(null);

      UserNotFoundException exception = assertThrows(UserNotFoundException.class,
          () -> userService.updateUser(updateRequest));
      assertTrue(exception.getMessage().contains("1"));

      verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should throw ConflictException when email belongs to another user")
    void shouldThrowConflictExceptionWhenEmailBelongsToAnotherUser() {
      User existingUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .build();

      User anotherUser = User.builder()
          .id(2L)
          .name("Jane Doe")
          .email("jane@example.com")
          .build();

      User updateRequest = User.builder()
          .id(1L)
          .name("John Updated")
          .email("jane@example.com")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(existingUser);
      when(userRepositoryPort.findByEmail("jane@example.com")).thenReturn(anotherUser);

      ConflictException exception = assertThrows(ConflictException.class,
          () -> userService.updateUser(updateRequest));
      assertTrue(exception.getMessage().contains("jane@example.com"));
      assertTrue(exception.getMessage().contains("already exists"));

      verify(userRepositoryPort, never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("deleteUser")
  class DeleteUser {

    @Test
    @DisplayName("should delete user when found")
    void shouldDeleteUser() {
      User user = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .build();

      when(userRepositoryPort.find(1L)).thenReturn(user);

      userService.deleteUser(1L);

      verify(userRepositoryPort).delete(1L);
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowUserNotFoundException() {
      when(userRepositoryPort.find(1L)).thenReturn(null);

      UserNotFoundException exception = assertThrows(UserNotFoundException.class,
          () -> userService.deleteUser(1L));
      assertTrue(exception.getMessage().contains("1"));

      verify(userRepositoryPort, never()).delete(any());
    }
  }
}
