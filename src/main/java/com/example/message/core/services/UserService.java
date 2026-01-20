package com.example.message.core.services;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.UserNotFoundException;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.UserRepositoryPort;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class UserService implements UserUseCase {
  private final UserRepositoryPort userRepositoryPort;
  private final BCryptPasswordEncoder passwordEncoder;

  public UserService(UserRepositoryPort userRepositoryPort, BCryptPasswordEncoder passwordEncoder) {
    this.userRepositoryPort = userRepositoryPort;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public User createUser(User user) {
    // Hash the password before saving
    if (user.getPassword() != null && !user.getPassword().isEmpty()) {
      user.setPassword(passwordEncoder.encode(user.getPassword()));
    }
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

  @Override
  public User updateUser(User user) {
    User existing = userRepositoryPort.find(user.getId());
    if (existing == null) {
      throw new UserNotFoundException("User not found with id: " + user.getId());
    }
    return userRepositoryPort.save(user);
  }

  @Override
  public void deleteUser(Long id) {
    User existing = userRepositoryPort.find(id);
    if (existing == null) {
      throw new UserNotFoundException("User not found with id: " + id);
    }
    userRepositoryPort.delete(id);
  }
}
