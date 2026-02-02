package com.example.message.core.services;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.ConflictException;
import com.example.message.core.exceptions.UserNotFoundException;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.core.ports.output.UserRepositoryPort;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Transactional(readOnly = true)
public class UserService implements UserUseCase {
  private final UserRepositoryPort userRepositoryPort;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepositoryPort userRepositoryPort, PasswordEncoder passwordEncoder) {
    this.userRepositoryPort = userRepositoryPort;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public User createUser(User user) {
    log.debug("Creating user with email: {}", user.getEmail());

    User existingEmail = userRepositoryPort.findByEmail(user.getEmail());

    if (existingEmail != null) {
      log.warn("Attempted to create user with existing email: {}", user.getEmail());
      throw new ConflictException("User with email " + user.getEmail() + " already exists");
    }

    String hashedPassword = passwordEncoder.encode(user.getPassword());

    user.setPassword(hashedPassword);

    User savedUser = userRepositoryPort.save(user);

    log.info("Created user with id: {}", savedUser.getId());

    return savedUser;
  }

  @Override
  @Transactional
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
  @Transactional
  public User updateUser(User user) {
    log.debug("Updating user with id: {}", user.getId());

    User existing = userRepositoryPort.find(user.getId());

    if (existing == null) {
      log.warn("Update failed - user not found with id: {}", user.getId());
      throw new UserNotFoundException("User not found with id: " + user.getId());
    }

    User existingEmail = userRepositoryPort.findByEmail(user.getEmail());

    if (existingEmail != null && !existingEmail.getId().equals(user.getId())) {
      log.warn("Update failed - email already in use: {}", user.getEmail());
      throw new ConflictException("User with email " + user.getEmail() + " already exists");
    }

    String password = user.getPassword();

    if (password != null && !password.isEmpty()) {
      password = passwordEncoder.encode(password);
    }

    existing.updateFields(user.getName(), user.getEmail(), password);

    User updatedUser = userRepositoryPort.save(user);

    log.info("Updated user with id: {}", updatedUser.getId());

    return updatedUser;
  }

  @Override
  @Transactional
  public void deleteUser(Long id) {
    log.debug("Deleting user with id: {}", id);
    User existing = userRepositoryPort.find(id);

    if (existing == null) {
      log.warn("Delete failed - user not found with id: {}", id);
      throw new UserNotFoundException("User not found with id: " + id);
    }

    userRepositoryPort.delete(id);
    log.info("Deleted user with id: {}", id);
  }
}
