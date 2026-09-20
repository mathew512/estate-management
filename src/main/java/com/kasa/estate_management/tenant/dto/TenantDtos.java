package com.kasa.estate_management.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class TenantDtos {

    public record OnboardTenantRequest(
        @NotBlank(message = "Full name is required") String fullName,
        @NotBlank(message = "Phone number is required") String phoneNumber,
        @Email(message = "Email must be valid") String email,
        String nationalId
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