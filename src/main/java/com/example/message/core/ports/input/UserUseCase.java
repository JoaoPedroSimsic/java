package com.example.message.core.ports.input;

import com.example.message.core.domain.User;
import java.util.List;

public interface UserUseCase {
  User createUser(User user);

  List<User> listUsers();

  User findById(Long id);

  User findByEmail(String email);

  User updateUser(User user);

  void deleteUser(Long id);
}
