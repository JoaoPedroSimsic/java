package com.example.message.infrastructure.adapters.output.db;

import com.example.message.core.domain.User;
import com.example.message.core.ports.output.UserRepositoryPort;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PostgresUserRepository implements UserRepositoryPort {
  private final JpaUserRepo repository;

  public PostgresUserRepository(JpaUserRepo repository) {
    this.repository = repository;
  }

  @Override
  public User save(User user) {
    UserEntity entity = new UserEntity();

    entity.setId(user.getId());
    entity.setName(user.getName());
    entity.setEmail(user.getEmail());

    UserEntity saved = repository.save(entity);

    return new User(saved.getId(), saved.getName(), saved.getEmail());
  }

  @Override
  public List<User> findAll() {
    return repository.findAll().stream()
        .map(e -> new User(e.getId(), e.getName(), e.getEmail()))
        .collect(Collectors.toList());
  }

  @Override
  public User find(Long id) {
    return repository
        .findById(id)
        .map(e -> new User(e.getId(), e.getName(), e.getEmail()))
        .orElse(null);
  }

  @Override
  public User findByEmail(String email) {
    return repository
        .findByEmail(email)
        .map(e -> new User(e.getId(), e.getName(), e.getEmail()))
        .orElse(null);
  }

  @Override
  public void delete(Long id) {
    repository.deleteById(id);
  }
}
