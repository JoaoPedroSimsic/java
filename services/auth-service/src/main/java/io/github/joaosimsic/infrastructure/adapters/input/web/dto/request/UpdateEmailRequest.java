package io.github.joaosimsic.infrastructure.adapters.input.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateEmailRequest {

  @NotBlank(message = "New email is required")
  @Email(message = "Invalid email format")
  private String newEmail;
}
