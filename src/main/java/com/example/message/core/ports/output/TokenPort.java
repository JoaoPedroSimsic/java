package com.example.message.core.ports.output;

import com.example.message.core.domain.User;

public interface TokenPort {
  String generateToken(User user);

  String validateToken(String token);
}
