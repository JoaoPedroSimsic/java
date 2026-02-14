package io.github.joaosimsic.core.ports.input;

import io.github.joaosimsic.core.domain.User;
import java.util.List;

public interface UserUseCase {
  User createUser(User user);

  List<User> listUsers();

  User findById(Long id);

  User findByEmail(String email);

  User findByExternalId(String externalId);

  User syncUser(String externalId, String email, String name);

  User updateUser(User user);

  User updateUserName(Long id, String name);

  void deleteUser(Long id);

  void updateEmailByExternalId(String externalId, String newEmail);
}
