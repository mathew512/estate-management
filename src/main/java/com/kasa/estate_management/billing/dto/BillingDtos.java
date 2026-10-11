package com.kasa.estate_management.billing.dto;

import com.kasa.estate_management.billing.model.InvoiceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class BillingDtos {

    public record RecordMeterReadingRequest(
        @NotNull(message = "Unit ID is required") UUID unitId,
        @NotNull @DecimalMin(value = "0.0", message = "Reading cannot be negative") BigDecimal currentReading,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Rate must be greater than 0") BigDecimal ratePerUnit,
        @NotNull(message = "Reading date is required") LocalDate readingDate
    ) {}

    public record MeterReadingResponse(
        UUID id,
        UUID unitId,
        LocalDate readingDate,
        BigDecimal previousReading,
        BigDecimal currentReading,
        BigDecimal ratePerUnit,
        BigDecimal totalCharge,
        Instant createdAt
    ) {}

    public record GenerateInvoiceRequest(
        @NotNull(message = "Lease ID is required") UUID leaseId,
        @NotNull(message = "Period month is required") LocalDate periodMonth,
        BigDecimal utilityAmount
    ) {}

    public record ApplyPenaltyRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Penalty must be greater than 0") BigDecimal penaltyAmount
    ) {}

    public record InvoiceResponse(
        UUID id,
        UUID leaseId,
        LocalDate periodMonth,
        BigDecimal rentAmount,
        BigDecimal utilityAmount,
        BigDecimal penaltyAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        InvoiceStatus status,
        LocalDate dueDate,
        Instant createdAt
    ) {}
}