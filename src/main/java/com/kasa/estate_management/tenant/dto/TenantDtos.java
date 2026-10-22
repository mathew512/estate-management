package com.kasa.estate_management.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class TenantDtos {

    public record OnboardTenantRequest(
        @NotBlank(message = "Full name is required") String fullName,
        @NotBlank(message = "Phone number is required") String phoneNumber,
        @Email(message = "Email must be valid") String email,
        String nationalId
    ) {}

    public record CreateTenantLoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "A valid email is required") String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password
    ) {}

    public record TenantResponse(
        UUID id,
        String fullName,
        String phoneNumber,
        String email,
        String nationalId,
        boolean active,
        Instant createdAt
    ) {}
}