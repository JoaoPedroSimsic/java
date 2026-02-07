package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.domain.User;
import java.util.List;

public interface UserPort {
  User save(User user);

  List<User> findAll();

  User find(Long id);

  User findByEmail(String email);

  void delete(Long id);
}
