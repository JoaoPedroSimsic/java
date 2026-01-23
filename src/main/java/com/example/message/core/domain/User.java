package com.example.message.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
  private Long id;
  private String name;
  private String email;

  public void updateFields(String newName, String newEmail) {
    if (newName != null && !newName.isBlank()) {
      this.name = newName;
    }

    if (newEmail != null && !newEmail.isBlank()) {
      this.email = newEmail;
    }
  }
}
