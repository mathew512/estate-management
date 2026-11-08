package com.kasa.estate_management.billing.service;

import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.repository.InvoiceRepository;
import com.kasa.estate_management.payment.model.Payment;
import com.kasa.estate_management.payment.model.PaymentMethod;
import com.kasa.estate_management.payment.model.PaymentStatus;
import com.kasa.estate_management.payment.repository.PaymentRepository;
import com.kasa.estate_management.payment.service.PaymentService;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.service.TenantService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TenantBillingService {

    private static final Duration PROMPT_COOLDOWN = Duration.ofMinutes(3);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final TenantService tenantService;

    public TenantBillingService(InvoiceRepository invoiceRepository,
                                PaymentRepository paymentRepository,
                                PaymentService paymentService,
                                TenantService tenantService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.tenantService = tenantService;
    }

    public List<Invoice> list(User user) {
        Tenant tenant = tenantService.getByUserId(user.getId());
        return invoiceRepository.findByLeaseTenantIdOrderByPeriodMonthDesc(tenant.getId());
    }

    public Invoice get(User user, UUID invoiceId) {
        Tenant tenant = tenantService.getByUserId(user.getId());
        return ownedInvoice(tenant, invoiceId);
    }

    public List<Payment> payments(User user, UUID invoiceId) {
        Tenant tenant = tenantService.getByUserId(user.getId());
        ownedInvoice(tenant, invoiceId);
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    /**
     * Starts an M-Pesa prompt to the tenant's own registered number. Deliberately not
     * transactional: the underlying call waits on Safaricom, so no database transaction is held open.
     */
    public Payment pay(User user, UUID invoiceId, BigDecimal amount) {
        Tenant tenant = tenantService.getByUserId(user.getId());
        ownedInvoice(tenant, invoiceId);

        Instant cutoff = Instant.now().minus(PROMPT_COOLDOWN);
        boolean recentPrompt = paymentRepository.findByInvoiceIdAndStatus(invoiceId, PaymentStatus.PENDING)
            .stream()
            .anyMatch(p -> p.getMethod() == PaymentMethod.MPESA && p.getCreatedAt().isAfter(cutoff));
        if (recentPrompt) {
            throw new IllegalArgumentException(
                "A payment prompt was already sent for this invoice. Check your phone, or try again in a few minutes.");
        }

        return paymentService.initiateMpesaPayment(invoiceId, tenant.getPhoneNumber(), amount);
    }

    /** A missing invoice and someone else's invoice look identical, so IDs can't be probed. */
    private Invoice ownedInvoice(Tenant tenant, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        if (!invoice.getLease().getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
        return invoice;
    }
}