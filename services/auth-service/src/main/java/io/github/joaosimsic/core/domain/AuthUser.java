package io.github.joaosimsic.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthUser {
  private String id;
  private String email;
  private String name;
  private boolean emailVerified;
}
