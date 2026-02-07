package io.github.joaosimsic.core.ports.input;

import io.github.joaosimsic.core.domain.User;
import java.util.List;

public interface UserUseCase {
  User createUser(User user);

  List<User> listUsers();

  User findById(Long id);

  User findByEmail(String email);

  User updateUser(User user);

  void deleteUser(Long id);
}
