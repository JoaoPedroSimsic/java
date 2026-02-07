package io.github.joaosimsic.infrastructure.adapters.input.web.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
    @NotBlank(message = "Name cannot be empty")
        @Size(min = 4, max = 50, message = "Name must be between 4 and 50 characters")
        String name,
    @NotBlank(message = "Email cannot be empty") @Email(message = "Invalid email format")
        String email,
    @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
        String password) {}
