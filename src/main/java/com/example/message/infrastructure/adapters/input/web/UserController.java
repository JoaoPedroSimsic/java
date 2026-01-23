package com.example.message.infrastructure.adapters.input.web;

import com.example.message.core.domain.User;
import com.example.message.core.ports.input.UserUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
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
  public ResponseEntity<User> create(@Valid @RequestBody UserRequest request) {
    User domainUser = new User(null, request.name(), request.email());
    User savedUser = userUseCase.createUser(domainUser);

    return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
  }

  @GetMapping
  public ResponseEntity<List<User>> getAll() {
    List<User> users = userUseCase.listUsers();

    return ResponseEntity.ok(users);
  }

  @GetMapping("/{id}")
  public ResponseEntity<User> find(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id) {
    return ResponseEntity.ok(userUseCase.findById(id));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<User> update(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id,
      @Valid @RequestBody UserRequest request) {
    User domainUser = new User(id, request.name(), request.email());
    return ResponseEntity.ok(userUseCase.updateUser(domainUser));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id) {
    userUseCase.deleteUser(id);
    return ResponseEntity.noContent().build();
  }
}
