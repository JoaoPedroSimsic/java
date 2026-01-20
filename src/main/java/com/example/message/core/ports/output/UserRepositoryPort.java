package com.example.message.core.ports.output;

import com.example.message.core.domain.User;
import java.util.List;

public interface UserRepositoryPort {
  User save(User user);
  List<User> findAll();
  User find(Long id);
  User findByEmail(String email);
  void delete(Long id);
}
