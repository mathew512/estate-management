package com.kasa.estate_management.billing.scheduler;

import com.kasa.estate_management.billing.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    private final InvoiceService invoiceService;

    public BillingScheduler(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // 1st of every month at 01:00 Nairobi time
    @Scheduled(cron = "0 0 1 1 * *", zone = "Africa/Nairobi")
    public void generateMonthlyInvoices() {
        LocalDate today = LocalDate.now(ZONE);
        int created = invoiceService.generateMonthlyInvoices(today);
        log.info("Monthly invoice job: created {} invoices for {}", created, today.withDayOfMonth(1));
    }

    // Every day at 02:00 Nairobi time
    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Nairobi")
    public void markOverdueInvoices() {
        int flagged = invoiceService.markOverdueInvoices(LocalDate.now(ZONE));
        log.info("Overdue sweep: flagged {} invoices as OVERDUE", flagged);
    }
}