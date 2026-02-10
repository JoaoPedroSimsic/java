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
        .build();
  }

  @Nested
  @DisplayName("updateFields")
  class UpdateFields {

    @Test
    @DisplayName("should update all fields when all parameters are valid")
    void shouldUpdateAllFields() {
      user.updateFields("Jane Doe", "jane@example.com");

      assertEquals("Jane Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
    }

    @Test
    @DisplayName("should update only name when other parameters are null")
    void shouldUpdateOnlyName() {
      user.updateFields("Jane Doe", null);

      assertEquals("Jane Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
    }

    @Test
    @DisplayName("should update only email when other parameters are null")
    void shouldUpdateOnlyEmail() {
      user.updateFields(null, "jane@example.com");

      assertEquals("John Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
    }

    @Test
    @DisplayName("should not update fields when all parameters are null")
    void shouldNotUpdateWhenAllNull() {
      user.updateFields(null, null);

      assertEquals("John Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
    }

    @Test
    @DisplayName("should not update name when value is blank")
    void shouldNotUpdateNameWhenBlank() {
      user.updateFields("   ", "jane@example.com");

      assertEquals("John Doe", user.getName());
      assertEquals("jane@example.com", user.getEmail());
    }

    @Test
    @DisplayName("should not update email when value is blank")
    void shouldNotUpdateEmailWhenBlank() {
      user.updateFields("Jane Doe", "");

      assertEquals("Jane Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
    }

    @Test
    @DisplayName("should not update any field when all values are blank")
    void shouldNotUpdateWhenAllBlank() {
      user.updateFields("", "   ");

      assertEquals("John Doe", user.getName());
      assertEquals("john@example.com", user.getEmail());
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
          .build();

      assertEquals(1L, builtUser.getId());
      assertEquals("John Doe", builtUser.getName());
      assertEquals("john@example.com", builtUser.getEmail());
    }

    @Test
    @DisplayName("should create user without id")
    void shouldCreateUserWithoutId() {
      User builtUser = User.builder()
          .name("John Doe")
          .email("john@example.com")
          .build();

      assertNull(builtUser.getId());
      assertEquals("John Doe", builtUser.getName());
      assertEquals("john@example.com", builtUser.getEmail());
    }
  }
}
