package com.example.message.infrastructure.adapters.output.db.repositories;

import com.example.message.core.domain.User;
import com.example.message.core.ports.output.UserRepositoryPort;
import com.example.message.infrastructure.adapters.output.db.entities.UserEntity;
import com.example.message.infrastructure.adapters.output.db.jpa.JpaUserRepo;
import java.util.List;
import java.util.stream.Collectors;
import java.sql.SQLException;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class JpaUserRepository implements UserRepositoryPort {
  private final JpaUserRepo repository;

  public JpaUserRepository(JpaUserRepo repository) {
    this.repository = repository;
  }

  @Override
  @Retryable(retryFor = { TransientDataAccessException.class,
      SQLException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  public User save(User user) {
    UserEntity entity = new UserEntity();

    entity.setId(user.getId());
    entity.setName(user.getName());
    entity.setEmail(user.getEmail());
    entity.setPassword(user.getPassword());

    UserEntity saved = repository.save(entity);

    return User.builder().id(saved.getId()).name(saved.getName()).email(saved.getEmail()).build();
  }

  @Override
  @Retryable(retryFor = { TransientDataAccessException.class,
      SQLException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  public List<User> findAll() {
    return repository.findAll().stream()
        .map(e -> User.builder().id(e.getId()).name(e.getName()).email(e.getEmail()).build())
        .collect(Collectors.toList());
  }

  @Override
  @Retryable(retryFor = { TransientDataAccessException.class,
      SQLException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  public User find(Long id) {
    return repository
        .findById(id)
        .map(e -> User.builder().id(e.getId()).name(e.getName()).email(e.getEmail()).build())
        .orElse(null);
  }

  @Override
  @Retryable(retryFor = { TransientDataAccessException.class,
      SQLException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  public User findByEmail(String email) {
    return repository
        .findByEmail(email)
        .map(
            e -> User.builder()
                .id(e.getId())
                .name(e.getName())
                .email(e.getEmail())
                .password(e.getPassword())
                .build())
        .orElse(null);
  }

  @Override
  @Retryable(retryFor = { TransientDataAccessException.class,
      SQLException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  public void delete(Long id) {
    repository.deleteById(id);
  }
}
