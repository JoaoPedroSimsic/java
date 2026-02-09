package io.github.joaosimsic.infrastructure.adapters.input.web.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserSyncRequest(
    @NotBlank(message = "External ID cannot be empty") String externalId,
    @NotBlank(message = "Email cannot be empty") @Email(message = "Invalid email format")
        String email,
    String name) {}
