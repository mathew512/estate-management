package com.kasa.estate_management.tenant.dto;

import com.kasa.estate_management.tenant.model.LeaseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class LeaseDtos {

    public record CreateLeaseRequest(
        @NotNull(message = "Unit ID is required") UUID unitId,
        @NotNull(message = "Tenant ID is required") UUID tenantId,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Monthly rent must be greater than 0") BigDecimal monthlyRent,
        @DecimalMin(value = "0.0", message = "Deposit cannot be negative") BigDecimal depositAmount
    ) {}

    public record LeaseResponse(
        UUID id,
        UUID unitId,
        String unitNumber,
        UUID tenantId,
        String tenantName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        LeaseStatus status,
        Instant createdAt
    ) {}
}