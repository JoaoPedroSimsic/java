package com.example.message.infrastructure.adapters.input.web.responses;

import com.example.message.core.domain.User;

public record UserResponse(Long id, String name, String email) {
  public static UserResponse fromDomain(User user) {
    return new UserResponse(user.getId(), user.getName(), user.getEmail());
  }
}
