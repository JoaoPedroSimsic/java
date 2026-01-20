package com.example.message.core.services;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.UserNotFoundException;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.UserRepositoryPort;
import java.util.List;

public class UserService implements UserUseCase {
  private final UserRepositoryPort userRepositoryPort;

  public UserService(UserRepositoryPort userRepositoryPort) {
    this.userRepositoryPort = userRepositoryPort;
  }

  @Override
  public User createUser(User user) {
    return userRepositoryPort.save(user);
  }

  @Override
  public List<User> listUsers() {
    return userRepositoryPort.findAll();
  }

  @Override
  public User findById(Long id) {
    User user = userRepositoryPort.find(id);
    if (user == null) {
      throw new UserNotFoundException("User not found with id: " + id);
    }
    return user;
  }
}
