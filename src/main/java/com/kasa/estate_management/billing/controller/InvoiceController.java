package com.kasa.estate_management.billing.controller;

import com.kasa.estate_management.billing.dto.BillingDtos.*;
import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.model.InvoiceStatus;
import com.kasa.estate_management.billing.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/generate")
    public ResponseEntity<InvoiceResponse> generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        Invoice invoice = invoiceService.generateInvoice(
            request.leaseId(), request.periodMonth(), request.utilityAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(invoice));
    }

    @PatchMapping("/{id}/penalty")
    public InvoiceResponse applyPenalty(@PathVariable UUID id, @Valid @RequestBody ApplyPenaltyRequest request) {
        return toResponse(invoiceService.applyPenalty(id, request.penaltyAmount()));
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return toResponse(invoiceService.getById(id));
    }

    @GetMapping
    public List<InvoiceResponse> getByStatus(@RequestParam(required = false) InvoiceStatus status) {
        List<Invoice> invoices = (status != null)
            ? invoiceService.getByStatus(status)
            : invoiceService.getByStatus(InvoiceStatus.PENDING);
        return invoices.stream().map(this::toResponse).toList();
    }

    private InvoiceResponse toResponse(Invoice i) {
        return new InvoiceResponse(
            i.getId(), i.getLease().getId(), i.getPeriodMonth(),
            i.getRentAmount(), i.getUtilityAmount(), i.getPenaltyAmount(), i.getTotalAmount(),
            i.getAmountPaid(), i.getStatus(), i.getDueDate(), i.getCreatedAt()
        );
    }
}