package com.example.message.infrastructure.adapters.input.web.controllers;

import com.example.message.core.domain.User;
import com.example.message.core.ports.input.UserUseCase;
import com.example.message.infrastructure.adapters.input.web.requests.UserRequest;
import com.example.message.infrastructure.adapters.input.web.responses.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
  private final UserUseCase userUseCase;

  public UserController(UserUseCase userUseCase) {
    this.userUseCase = userUseCase;
  }

  @PostMapping
  public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
    User domainUser =
        User.builder()
            .name(request.name())
            .email(request.email())
            .password(request.password())
            .build();

    User savedUser = userUseCase.createUser(domainUser);

    UserResponse response = UserResponse.fromDomain(savedUser);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  public ResponseEntity<List<UserResponse>> getAll() {
    List<UserResponse> responses =
        userUseCase.listUsers().stream().map(UserResponse::fromDomain).toList();

    return ResponseEntity.ok(responses);
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> find(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id) {
    User user = userUseCase.findById(id);
    return ResponseEntity.ok(UserResponse.fromDomain(user));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<UserResponse> update(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id,
      @Valid @RequestBody UserRequest request) {
    User domainUser =
        User.builder()
            .name(request.name())
            .email(request.email())
            .password(request.password())
            .build();
    User updatedUser = userUseCase.updateUser(domainUser);

    return ResponseEntity.ok(UserResponse.fromDomain(updatedUser));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id) {
    userUseCase.deleteUser(id);

    return ResponseEntity.noContent().build();
  }
}
