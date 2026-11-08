package com.kasa.estate_management.billing.dto;

import com.kasa.estate_management.billing.model.InvoiceStatus;
import com.kasa.estate_management.payment.model.PaymentMethod;
import com.kasa.estate_management.payment.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class TenantBillingDtos {

    public record MyInvoiceResponse(
        UUID id,
        String unitNumber,
        LocalDate periodMonth,
        BigDecimal rentAmount,
        BigDecimal utilityAmount,
        BigDecimal penaltyAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balance,
        InvoiceStatus status,
        LocalDate dueDate
    ) {}

    public record MyPaymentResponse(
        UUID id,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        String mpesaReceiptNumber,
        Instant paidAt,
        Instant createdAt
    ) {}

    public record PayRequest(
        @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0") BigDecimal amount
    ) {}

    public record PayResponse(UUID paymentId, PaymentStatus status, String message) {}
}