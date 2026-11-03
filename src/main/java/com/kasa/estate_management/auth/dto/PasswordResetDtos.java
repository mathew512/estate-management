package com.kasa.estate_management.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetDtos {

    public record RequestReset(
        @NotBlank(message = "Phone number is required") String phoneNumber
    ) {}

    public record ConfirmReset(
        @NotBlank(message = "Phone number is required") String phoneNumber,
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String code,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
    ) {}
}