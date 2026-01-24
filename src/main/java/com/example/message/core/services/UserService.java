package com.example.message.core.services;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.ConflictException;
import com.example.message.core.exceptions.UserNotFoundException;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.UserRepositoryPort;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserService implements UserUseCase {
  private final UserRepositoryPort userRepositoryPort;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepositoryPort userRepositoryPort, PasswordEncoder passwordEncoder) {
    this.userRepositoryPort = userRepositoryPort;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public User createUser(User user) {
    User existingEmail = userRepositoryPort.findByEmail(user.getEmail());

    if (existingEmail != null) {
      throw new ConflictException("User with email " + user.getEmail() + " already exists");
    }

    String hashedPassword = passwordEncoder.encode(user.getPassword());

    user.setPassword(hashedPassword);

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
  public User findByEmail(String email) {
    User user = userRepositoryPort.findByEmail(email);

    if (user == null) {
      throw new UserNotFoundException("User not found with email: " + email);
    }

    return user;
  }

  @Override
  public User updateUser(User user) {
    User existing = userRepositoryPort.find(user.getId());

    if (existing == null) {
      throw new UserNotFoundException("User not found with id: " + user.getId());
    }

    User existingEmail = userRepositoryPort.findByEmail(user.getEmail());

    if (existingEmail != null && !existingEmail.getId().equals(user.getId())) {
      throw new ConflictException("User with email " + user.getEmail() + " already exists");
    }

    String password = user.getPassword();

    if (password != null && !password.isEmpty()) {
      password = passwordEncoder.encode(password);
    }

    existing.updateFields(user.getName(), user.getEmail(), password);

    return userRepositoryPort.save(existing);
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
