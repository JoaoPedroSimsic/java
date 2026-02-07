package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.domain.User;

public interface TokenPort {
  String generateToken(User user);

  String validateToken(String token);
}
