package io.github.joaosimsic.infrastructure.adapters.input.web.filters;

import java.security.Principal;

public record GatewayPrincipal(String userId, String email) implements Principal {

  @Override
  public String getName() {
    return userId;
  }
}
