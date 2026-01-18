package com.example.message.infrastructure.adapters.input.web;

import com.example.message.core.domain.User;
import com.example.message.core.ports.input.UserUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserUseCase userUseCase;

  public UserController(UserUseCase userUseCase) {
    this.userUseCase = userUseCase;
  }

  @PostMapping
  public User create(@RequestBody User user) {
    return userUseCase.createUser(user);
  }

  @GetMapping
  public List<User> getAll() {
    return userUseCase.listUsers();
  }
}
