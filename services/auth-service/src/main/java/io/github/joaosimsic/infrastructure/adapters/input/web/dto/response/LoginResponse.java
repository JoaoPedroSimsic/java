package io.github.joaosimsic.infrastructure.adapters.input.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponse {
  private String message;
  private UserResponse user;
  private String accessToken;
}
