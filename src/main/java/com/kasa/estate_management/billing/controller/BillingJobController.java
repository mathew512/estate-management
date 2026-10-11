package com.kasa.estate_management.billing.controller;

import com.kasa.estate_management.billing.service.InvoiceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingJobController {

    private final InvoiceService invoiceService;

    public BillingJobController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/run-monthly-invoices")
    public Map<String, Object> runMonthlyInvoices(
        @RequestParam("month") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month
    ) {
        int created = invoiceService.generateMonthlyInvoices(month);
        return Map.of("month", month.withDayOfMonth(1).toString(), "invoicesCreated", created);
    }

    @PostMapping("/run-overdue-sweep")
    public Map<String, Object> runOverdueSweep() {
        int flagged = invoiceService.markOverdueInvoices(LocalDate.now());
        return Map.of("invoicesFlaggedOverdue", flagged);
    }
}