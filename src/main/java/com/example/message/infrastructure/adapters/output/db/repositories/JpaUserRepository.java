package com.example.message.infrastructure.adapters.output.db.repositories;

import com.example.message.core.domain.User;
import com.example.message.core.ports.output.UserRepositoryPort;
import com.example.message.infrastructure.adapters.output.db.entities.UserEntity;
import com.example.message.infrastructure.adapters.output.db.jpa.JpaUserRepo;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JpaUserRepository implements UserRepositoryPort {
  private final JpaUserRepo repository;

  public JpaUserRepository(JpaUserRepo repository) {
    this.repository = repository;
  }

  @Override
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
  public List<User> findAll() {
    return repository.findAll().stream()
        .map(e -> User.builder().id(e.getId()).name(e.getName()).email(e.getEmail()).build())
        .collect(Collectors.toList());
  }

  @Override
  public User find(Long id) {
    return repository
        .findById(id)
        .map(e -> User.builder().id(e.getId()).name(e.getName()).email(e.getEmail()).build())
        .orElse(null);
  }

  @Override
  public User findByEmail(String email) {
    return repository
        .findByEmail(email)
        .map(
            e ->
                User.builder()
                    .id(e.getId())
                    .name(e.getName())
                    .email(e.getEmail())
                    .password(e.getPassword())
                    .build())
        .orElse(null);
  }

  @Override
  public void delete(Long id) {
    repository.deleteById(id);
  }
}
