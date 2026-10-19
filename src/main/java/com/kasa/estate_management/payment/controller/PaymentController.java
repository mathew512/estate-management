package com.kasa.estate_management.payment.controller;

import com.kasa.estate_management.payment.dto.PaymentDtos.ManualPaymentRequest;
import com.kasa.estate_management.payment.dto.PaymentDtos.PaymentResponse;
import com.kasa.estate_management.payment.dto.PaymentDtos.StkPushRequest;
import com.kasa.estate_management.payment.dto.PaymentDtos.StkPushResponse;
import com.kasa.estate_management.payment.model.Payment;
import com.kasa.estate_management.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/manual")
    public ResponseEntity<PaymentResponse> recordManual(@Valid @RequestBody ManualPaymentRequest request) {
        Payment payment = paymentService.recordManualPayment(
            request.invoiceId(), request.amount(), request.method(), request.reference());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(payment));
    }

    @PostMapping("/payments/mpesa/stk-push")
    public ResponseEntity<StkPushResponse> stkPush(@Valid @RequestBody StkPushRequest request) {
        Payment payment = paymentService.initiateMpesaPayment(
            request.invoiceId(), request.phoneNumber(), request.amount());

        StkPushResponse response = new StkPushResponse(
            payment.getId(),
            payment.getCheckoutRequestId(),
            payment.getStatus(),
            "Payment prompt sent to the customer's phone"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<PaymentResponse> getByInvoice(@PathVariable("invoiceId") UUID invoiceId) {
        return paymentService.getByInvoice(invoiceId).stream().map(this::toResponse).toList();
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
            p.getId(), p.getInvoice().getId(), p.getAmount(), p.getMethod(), p.getStatus(),
            p.getReference(), p.getMpesaReceiptNumber(), p.getPaidAt(), p.getCreatedAt()
        );
    }
}