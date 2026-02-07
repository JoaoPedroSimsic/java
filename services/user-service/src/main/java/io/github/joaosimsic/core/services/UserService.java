package io.github.joaosimsic.core.services;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.core.events.UserCreatedEvent;
import io.github.joaosimsic.core.events.UserDeletedEvent;
import io.github.joaosimsic.core.events.UserUpdatedEvent;
import io.github.joaosimsic.core.exceptions.business.*;
import io.github.joaosimsic.core.ports.input.UserUseCase;
import io.github.joaosimsic.core.ports.output.OutboxPort;
import io.github.joaosimsic.core.ports.output.UserPort;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
public class UserService implements UserUseCase {
  private final UserPort userPort;
  private final PasswordEncoder passwordEncoder;
  private final OutboxPort outboxPort;

  public UserService(UserPort userPort, PasswordEncoder passwordEncoder, OutboxPort outboxPort) {
    this.userPort = userPort;
    this.passwordEncoder = passwordEncoder;
    this.outboxPort = outboxPort;
  }

  @Override
  @Transactional
  public User createUser(User user) {
    log.debug("Creating user with email: {}", user.getEmail());

    User existingEmail = userPort.findByEmail(user.getEmail());

    if (existingEmail != null) {
      log.warn("Attempted to create user with existing email: {}", user.getEmail());
      throw new ConflictException("User with email " + user.getEmail() + " already exists");
    }

    String hashedPassword = passwordEncoder.encode(user.getPassword());

    user.setPassword(hashedPassword);

    User savedUser = userPort.save(user);

    UserCreatedEvent event =
        new UserCreatedEvent(savedUser.getId(), savedUser.getEmail(), savedUser.getName());

    outboxPort.save(event);

    return savedUser;
  }

  @Override
  @Transactional
  public List<User> listUsers() {
    return userPort.findAll();
  }

  @Override
  public User findById(Long id) {
    User user = userPort.find(id);

    if (user == null) {
      throw new UserNotFoundException("User not found with id: " + id);
    }

    return user;
  }

  @Override
  public User findByEmail(String email) {
    User user = userPort.findByEmail(email);

    if (user == null) {
      throw new UserNotFoundException("User not found with email: " + email);
    }

    return user;
  }

  @Override
  @Transactional
  public User updateUser(User user) {
    log.debug("Updating user with id: {}", user.getId());

    User existing = userPort.find(user.getId());

    if (existing == null) {
      log.warn("Update failed - user not found with id: {}", user.getId());
      throw new UserNotFoundException("User not found with id: " + user.getId());
    }

    User existingEmail = userPort.findByEmail(user.getEmail());

    if (existingEmail != null && !existingEmail.getId().equals(user.getId())) {
      log.warn("Update failed - email already in use: {}", user.getEmail());
      throw new ConflictException("User with email " + user.getEmail() + " already exists");
    }

    String password = user.getPassword();

    if (password != null && !password.isEmpty()) {
      password = passwordEncoder.encode(password);
    }

    existing.updateFields(user.getName(), user.getEmail(), password);

    User updatedUser = userPort.save(user);

    UserUpdatedEvent event =
        new UserUpdatedEvent(updatedUser.getId(), updatedUser.getEmail(), updatedUser.getName());

    outboxPort.save(event);

    log.info("Updated user with id: {}", updatedUser.getId());

    return updatedUser;
  }

  @Override
  @Transactional
  public void deleteUser(Long id) {
    log.debug("Deleting user with id: {}", id);
    User existing = userPort.find(id);

    if (existing == null) {
      log.warn("Delete failed - user not found with id: {}", id);
      throw new UserNotFoundException("User not found with id: " + id);
    }

    userPort.delete(id);

    UserDeletedEvent event = new UserDeletedEvent(id);

    outboxPort.save(event);

    log.info("Deleted user with id: {}", id);
  }
}
