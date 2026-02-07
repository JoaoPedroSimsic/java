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
    user = User.builder()
        .id(1L)
        .name("John Doe")
        .email("john@example.com")
        .password("password123")
        .build();
  }

  @Nested
  @DisplayName("updateFields")
  class UpdateFields {

    @Test
    @DisplayName("should update all fields when all parameters are valid")
    void shouldUpdateAllFields() {
      user.updateFields("Jane Doe", "jane@example.com", "newpassword");

      assertEquals("Jane Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
      assertEquals("newpassword", user.getPassword());
    }

    @Test
    @DisplayName("should update only name when other parameters are null")
    void shouldUpdateOnlyName() {
      user.updateFields("Jane Doe", null, null);

      assertEquals("Jane Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
      assertEquals("password123", user.getPassword());
    }

    @Test
    @DisplayName("should update only email when other parameters are null")
    void shouldUpdateOnlyEmail() {
      user.updateFields(null, "jane@example.com", null);

      assertEquals("John Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
      assertEquals("password123", user.getPassword());
    }

    @Test
    @DisplayName("should update only password when other parameters are null")
    void shouldUpdateOnlyPassword() {
      user.updateFields(null, null, "newpassword");

      assertEquals("John Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
      assertEquals("newpassword", user.getPassword());
    }

    @Test
    @DisplayName("should not update fields when all parameters are null")
    void shouldNotUpdateWhenAllNull() {
      user.updateFields(null, null, null);

      assertEquals("John Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
      assertEquals("password123", user.getPassword());
    }

    @Test
    @DisplayName("should not update name when value is blank")
    void shouldNotUpdateNameWhenBlank() {
      user.updateFields("   ", "jane@example.com", "newpassword");

      assertEquals("John Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
      assertEquals("newpassword", user.getPassword());
    }

    @Test
    @DisplayName("should not update email when value is blank")
    void shouldNotUpdateEmailWhenBlank() {
      user.updateFields("Jane Doe", "", "newpassword");

      assertEquals("Jane Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
      assertEquals("newpassword", user.getPassword());
    }

    @Test
    @DisplayName("should not update password when value is blank")
    void shouldNotUpdatePasswordWhenBlank() {
      user.updateFields("Jane Doe", "jane@example.com", "  ");

      assertEquals("Jane Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
      assertEquals("password123", user.getPassword());
    }

    @Test
    @DisplayName("should not update any field when all values are blank")
    void shouldNotUpdateWhenAllBlank() {
      user.updateFields("", "   ", "");

      assertEquals("John Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
      assertEquals("password123", user.getPassword());
    }
  }

  @Nested
  @DisplayName("builder")
  class Builder {

    @Test
    @DisplayName("should create user with all fields")
    void shouldCreateUserWithAllFields() {
      User builtUser = User.builder()
          .id(1L)
          .name("John Doe")
          .email("john@example.com")
          .password("password123")
          .build();

      assertEquals(1L, builtUser.getId());
      assertEquals("John Doe", builtUser.getName());
      assertEquals("john@example.com", builtUser.getEmail());
      assertEquals("password123", builtUser.getPassword());
    }

    @Test
    @DisplayName("should create user without id")
    void shouldCreateUserWithoutId() {
      User builtUser = User.builder()
          .name("John Doe")
          .email("john@example.com")
          .password("password123")
          .build();

      assertNull(builtUser.getId());
      assertEquals("John Doe", builtUser.getName());
      assertEquals("john@example.com", builtUser.getEmail());
      assertEquals("password123", builtUser.getPassword());
    }
  }
}
