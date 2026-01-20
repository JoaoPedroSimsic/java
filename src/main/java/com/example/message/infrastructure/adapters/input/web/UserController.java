package com.example.message.infrastructure.adapters.input.web;

import com.example.message.core.domain.User;
import com.example.message.core.ports.input.UserUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
  private final UserUseCase userUseCase;

  public UserController(UserUseCase userUseCase) {
    this.userUseCase = userUseCase;
  }

  @PostMapping
  public User create(@Valid @RequestBody UserRequest request) {
    User domainUser = new User(null, request.name(), request.email());
    return userUseCase.createUser(domainUser);
  }

  @GetMapping
  public List<User> getAll() {
    return userUseCase.listUsers();
  }

  @GetMapping("/{id}")
  public ResponseEntity<User> find(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id) {
    return ResponseEntity.ok(userUseCase.findById(id));
  }
}
