package com.kasa.estate_management.billing.controller;

import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.billing.dto.TenantBillingDtos.MyInvoiceResponse;
import com.kasa.estate_management.billing.dto.TenantBillingDtos.MyPaymentResponse;
import com.kasa.estate_management.billing.dto.TenantBillingDtos.PayRequest;
import com.kasa.estate_management.billing.dto.TenantBillingDtos.PayResponse;
import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.service.TenantBillingService;
import com.kasa.estate_management.payment.model.Payment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/invoices")
public class TenantBillingController {

    private final TenantBillingService tenantBillingService;

    public TenantBillingController(TenantBillingService tenantBillingService) {
        this.tenantBillingService = tenantBillingService;
    }

    @GetMapping
    public List<MyInvoiceResponse> list(@AuthenticationPrincipal User currentUser) {
        return tenantBillingService.list(currentUser).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public MyInvoiceResponse get(@PathVariable("id") UUID id, @AuthenticationPrincipal User currentUser) {
        return toResponse(tenantBillingService.get(currentUser, id));
    }

    @GetMapping("/{id}/payments")
    public List<MyPaymentResponse> payments(@PathVariable("id") UUID id,
                                            @AuthenticationPrincipal User currentUser) {
        return tenantBillingService.payments(currentUser, id).stream().map(this::toPaymentResponse).toList();
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PayResponse> pay(@PathVariable("id") UUID id,
                                           @RequestBody(required = false) @Valid PayRequest request,
                                           @AuthenticationPrincipal User currentUser) {
        BigDecimal amount = (request != null) ? request.amount() : null;
        Payment payment = tenantBillingService.pay(currentUser, id, amount);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PayResponse(
            payment.getId(), payment.getStatus(),
            "A payment prompt has been sent to your registered phone number."));
    }

    private MyInvoiceResponse toResponse(Invoice i) {
        BigDecimal total = i.getTotalAmount();
        return new MyInvoiceResponse(
            i.getId(),
            i.getLease().getUnit().getUnitNumber(),
            i.getPeriodMonth(),
            i.getRentAmount(),
            i.getUtilityAmount(),
            i.getPenaltyAmount(),
            total,
            i.getAmountPaid(),
            total.subtract(i.getAmountPaid()),
            i.getStatus(),
            i.getDueDate()
        );
    }

    private MyPaymentResponse toPaymentResponse(Payment p) {
        return new MyPaymentResponse(
            p.getId(), p.getAmount(), p.getMethod(), p.getStatus(),
            p.getReference(), p.getMpesaReceiptNumber(), p.getPaidAt(), p.getCreatedAt()
        );
    }
}