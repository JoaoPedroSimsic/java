package com.example.message.core.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
  private Long id;

  @NotBlank(message = "Name cannot be empty")
  @Size(min = 4, max = 50, message = "Name must be between 4 and 50 characters")
  private String name;

  @NotBlank(message = "Email cannot be empty")
  @Email(message = "Invalid email format")
  private String email;
}
