package com.kasa.estate_management.billing.service;

import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.model.InvoiceStatus;
import com.kasa.estate_management.billing.repository.InvoiceRepository;
import com.kasa.estate_management.billing.repository.MeterReadingRepository;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.LeaseStatus;
import com.kasa.estate_management.tenant.repository.LeaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final LeaseRepository leaseRepository;
    private final MeterReadingRepository meterReadingRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          LeaseRepository leaseRepository,
                          MeterReadingRepository meterReadingRepository) {
        this.invoiceRepository = invoiceRepository;
        this.leaseRepository = leaseRepository;
        this.meterReadingRepository = meterReadingRepository;
    }

    @Transactional
    public Invoice generateInvoice(UUID leaseId, LocalDate periodMonth, BigDecimal utilityAmount) {
        Lease lease = leaseRepository.findById(leaseId)
            .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot generate an invoice for a non-active lease");
        }

        LocalDate normalizedPeriod = periodMonth.withDayOfMonth(1);

        if (invoiceRepository.existsByLeaseIdAndPeriodMonth(leaseId, normalizedPeriod)) {
            throw new IllegalArgumentException(
                "An invoice already exists for this lease for " + normalizedPeriod);
        }

        BigDecimal utility = (utilityAmount != null) ? utilityAmount : BigDecimal.ZERO;

        Invoice invoice = Invoice.builder()
            .lease(lease)
            .periodMonth(normalizedPeriod)
            .rentAmount(lease.getMonthlyRent())
            .utilityAmount(utility)
            .penaltyAmount(BigDecimal.ZERO)
            .amountPaid(BigDecimal.ZERO)
            .status(InvoiceStatus.PENDING)
            .dueDate(normalizedPeriod.plusDays(4))
            .build();

        return invoiceRepository.save(invoice);
    }

    /**
     * Creates an invoice for every ACTIVE lease for the given month, skipping leases that
     * already have one. Utility is billed in arrears: the meter charges recorded during
     * the previous calendar month.
     */
    @Transactional
    public int generateMonthlyInvoices(LocalDate month) {
        LocalDate period = month.withDayOfMonth(1);
        LocalDate previousMonthStart = period.minusMonths(1);
        int created = 0;

        for (Lease lease : leaseRepository.findByStatus(LeaseStatus.ACTIVE)) {
            if (invoiceRepository.existsByLeaseIdAndPeriodMonth(lease.getId(), period)) {
                continue;
            }

            BigDecimal utility = meterReadingRepository.sumChargeForPeriod(
                lease.getUnit().getId(), previousMonthStart, period);

            generateInvoice(lease.getId(), period, utility);
            created++;
        }

        return created;
    }

    /**
     * Flags every unpaid or partially paid invoice whose due date has passed as OVERDUE.
     */
    @Transactional
    public int markOverdueInvoices(LocalDate today) {
        List<Invoice> late = invoiceRepository.findByStatusInAndDueDateBefore(
            List.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIALLY_PAID), today);

        late.forEach(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE));
        invoiceRepository.saveAll(late);

        return late.size();
    }

    @Transactional
    public Invoice applyPenalty(UUID invoiceId, BigDecimal penaltyAmount) {
        Invoice invoice = getById(invoiceId);

        invoice.setPenaltyAmount(invoice.getPenaltyAmount().add(penaltyAmount));
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice markOverdue(UUID invoiceId) {
        Invoice invoice = getById(invoiceId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalArgumentException("Cannot mark a fully paid invoice as overdue");
        }

        invoice.setStatus(InvoiceStatus.OVERDUE);
        return invoiceRepository.save(invoice);
    }

    public Invoice getById(UUID id) {
        return invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));
    }

    public List<Invoice> getByLease(UUID leaseId) {
        return invoiceRepository.findByLeaseId(leaseId);
    }

    public List<Invoice> getByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatus(status);
    }
}