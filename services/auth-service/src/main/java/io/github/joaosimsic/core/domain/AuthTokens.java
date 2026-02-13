package io.github.joaosimsic.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthTokens {
  private String accessToken;
  private String refreshToken;
  private String idToken;
  private int expiresIn;
  private int refreshExpiresIn;
}
