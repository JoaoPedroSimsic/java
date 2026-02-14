package io.github.joaosimsic.infrastructure.adapters.input.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank(message = "Name cannot be empty")
        @Size(min = 4, max = 50, message = "Name must be between 4 and 50 characters")
        String name) {}
