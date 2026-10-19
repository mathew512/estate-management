package com.kasa.estate_management.payment.dto;

import com.kasa.estate_management.payment.model.PaymentMethod;
import com.kasa.estate_management.payment.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentDtos {

    public record ManualPaymentRequest(
        @NotNull(message = "Invoice ID is required") UUID invoiceId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0") BigDecimal amount,
        @NotNull(message = "Payment method is required") PaymentMethod method,
        String reference
    ) {}

    public record StkPushRequest(
        @NotNull(message = "Invoice ID is required") UUID invoiceId,
        @NotBlank(message = "Phone number is required") String phoneNumber,
        @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0") BigDecimal amount
    ) {}

    public record StkPushResponse(
        UUID paymentId,
        String checkoutRequestId,
        PaymentStatus status,
        String message
    ) {}

    public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        String mpesaReceiptNumber,
        Instant paidAt,
        Instant createdAt
    ) {}
}