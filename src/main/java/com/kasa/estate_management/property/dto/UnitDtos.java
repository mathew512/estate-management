package com.kasa.estate_management.property.dto;

import com.kasa.estate_management.property.model.UnitStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class UnitDtos {

    public record CreateUnitRequest(
        @NotBlank(message = "Unit number is required") String unitNumber,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Base rent must be greater than 0") BigDecimal baseRent,
        String waterMeterNumber
    ) {}

    public record UpdateUnitStatusRequest(
        @NotNull(message = "Status is required") UnitStatus status
    ) {}

    public record UnitResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String unitNumber,
        BigDecimal baseRent,
        UnitStatus status,
        String waterMeterNumber
    ) {}
}