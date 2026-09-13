package com.kasa.estate_management.auth.dto;

import com.kasa.estate_management.auth.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class AuthDtos {

    public record RegisterRequest(
        @NotBlank(message = "Full name is required") String fullName,
        @NotBlank @Email(message = "A valid email is required") String email,
        String phoneNumber,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotNull(message = "Role is required") Role role
    ) {}

    public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        Role role,
        boolean active
    ) {}

    public record LoginRequest(
        @NotBlank @Email(message = "A valid email is required") String email,
        @NotBlank(message = "Password is required") String password
    ) {}

    public record LoginResponse(
        String token,
        String tokenType,
        UserResponse user
    ) {}
}