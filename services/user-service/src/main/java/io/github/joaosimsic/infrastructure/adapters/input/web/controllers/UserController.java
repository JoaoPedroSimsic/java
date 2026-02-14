package io.github.joaosimsic.infrastructure.adapters.input.web.controllers;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.core.ports.input.UserUseCase;
import io.github.joaosimsic.infrastructure.adapters.input.web.requests.UserRequest;
import io.github.joaosimsic.infrastructure.adapters.input.web.requests.UserSyncRequest;
import io.github.joaosimsic.infrastructure.adapters.input.web.requests.UserUpdateRequest;
import io.github.joaosimsic.infrastructure.adapters.input.web.responses.UserResponse;
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
    User domainUser = User.builder().name(request.name()).email(request.email()).build();

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
      @Valid @RequestBody UserUpdateRequest request) {
    User updatedUser = userUseCase.updateUserName(id, request.name());

    return ResponseEntity.ok(UserResponse.fromDomain(updatedUser));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable @Min(value = 1, message = "ID must be at least 1") Long id) {
    userUseCase.deleteUser(id);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/sync")
  public ResponseEntity<UserResponse> sync(@Valid @RequestBody UserSyncRequest request) {
    User user = userUseCase.syncUser(request.externalId(), request.email(), request.name());

    return ResponseEntity.ok(UserResponse.fromDomain(user));
  }
}
