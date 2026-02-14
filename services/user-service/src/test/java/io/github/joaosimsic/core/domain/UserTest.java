package io.github.joaosimsic.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

  private User user;

  @BeforeEach
  void setUp() {
    user = User.builder().id(1L).name("John Doe").email("john@example.com").build();
  }

  @Nested
  @DisplayName("builder")
  class Builder {

    @Test
    @DisplayName("should create user with all fields")
    void shouldCreateUserWithAllFields() {
      User builtUser = User.builder().id(1L).name("John Doe").email("john@example.com").build();

      assertEquals(1L, builtUser.getId());
      assertEquals("John Doe", builtUser.getName());
      assertEquals("john@example.com", builtUser.getEmail());
    }

    @Test
    @DisplayName("should create user without id")
    void shouldCreateUserWithoutId() {
      User builtUser = User.builder().name("John Doe").email("john@example.com").build();

      assertNull(builtUser.getId());
      assertEquals("John Doe", builtUser.getName());
      assertEquals("john@example.com", builtUser.getEmail());
    }
  }
}
