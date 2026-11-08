package com.kasa.estate_management.payment.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.model.InvoiceStatus;
import com.kasa.estate_management.billing.repository.InvoiceRepository;
import com.kasa.estate_management.notification.service.TenantNotifier;
import com.kasa.estate_management.payment.mpesa.MpesaClient;
import com.kasa.estate_management.payment.model.Payment;
import com.kasa.estate_management.payment.model.PaymentMethod;
import com.kasa.estate_management.payment.model.PaymentStatus;
import com.kasa.estate_management.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final MpesaClient mpesaClient;
    private final TenantNotifier tenantNotifier;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          MpesaClient mpesaClient,
                          TenantNotifier tenantNotifier,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.mpesaClient = mpesaClient;
        this.tenantNotifier = tenantNotifier;
        this.auditService = auditService;
    }

    @Transactional
    public Payment recordManualPayment(UUID invoiceId, BigDecimal amount, PaymentMethod method, String reference) {
        if (method == PaymentMethod.MPESA) {
            throw new IllegalArgumentException("M-Pesa payments are recorded automatically via STK Push");
        }

        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        applyToInvoice(invoice, amount);

        Payment payment = Payment.builder()
            .invoice(invoice)
            .amount(amount)
            .method(method)
            .status(PaymentStatus.SUCCESS)
            .reference(reference)
            .paidAt(Instant.now())
            .build();

        Payment saved = paymentRepository.save(payment);

        BigDecimal balance = invoice.getTotalAmount().subtract(invoice.getAmountPaid());
        String ref = (reference == null || reference.isBlank()) ? "" : " (ref " + shorten(reference, 60) + ")";
        auditService.record(AuditAction.PAYMENT_RECORDED, "INVOICE", invoice.getId(),
            "Recorded " + method + " payment " + saved.getId() + " of " + amount.toPlainString() + ref
                + "; invoice now " + invoice.getStatus() + ", balance " + balance.toPlainString());

        tenantNotifier.paymentReceived(saved, invoice);
        return saved;
    }

    /**
     * Sends an STK Push prompt to the customer's phone and records a PENDING payment.
     * Not transactional on purpose: we don't hold a database transaction open while
     * waiting on Safaricom. The payment only moves the invoice once the callback confirms it.
     */
    public Payment initiateMpesaPayment(UUID invoiceId, String phoneNumber, BigDecimal requestedAmount) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        BigDecimal balance = invoice.getTotalAmount().subtract(invoice.getAmountPaid());
        if (balance.signum() <= 0) {
            throw new IllegalArgumentException("This invoice is already fully paid");
        }

        BigDecimal amount = (requestedAmount != null) ? requestedAmount : balance;
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                "Payment of " + amount + " exceeds the outstanding balance of " + balance);
        }

        String accountReference = "INV-" + invoiceId.toString().substring(0, 8);

        MpesaClient.StkPushResult result;
        try {
            result = mpesaClient.stkPush(phoneNumber, amount, accountReference, "Rent payment");
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                "M-Pesa rejected the request (HTTP " + e.getStatusCode().value() + ")");
        }

        if (!"0".equals(result.responseCode())) {
            throw new IllegalStateException("M-Pesa did not accept the request: " + result.customerMessage());
        }

        Payment payment = Payment.builder()
            .invoice(invoice)
            .amount(amount)
            .method(PaymentMethod.MPESA)
            .status(PaymentStatus.PENDING)
            .phoneNumber(phoneNumber)
            .merchantRequestId(result.merchantRequestId())
            .checkoutRequestId(result.checkoutRequestId())
            .build();

        Payment saved = paymentRepository.save(payment);

        // The prompt is already on the customer's phone, so an audit problem must not fail the request.
        auditService.recordBestEffort(AuditAction.MPESA_PROMPT_SENT, "INVOICE", invoice.getId(),
            "Sent an M-Pesa prompt for payment " + saved.getId() + " of " + amount.toPlainString());

        return saved;
    }

    /**
     * Applies the result Safaricom reports for an STK Push. Safe to call more than once
     * for the same CheckoutRequestID: only a PENDING payment is ever changed.
     */
    @Transactional
    public void handleMpesaCallback(String checkoutRequestId, int resultCode, String resultDesc,
                                    BigDecimal paidAmount, String receiptNumber) {
        Optional<Payment> found = paymentRepository.findByCheckoutRequestIdForUpdate(checkoutRequestId);
        if (found.isEmpty()) {
            log.warn("M-Pesa callback for unknown CheckoutRequestID {}", checkoutRequestId);
            return;
        }

        Payment payment = found.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Ignoring repeated M-Pesa callback for {}", checkoutRequestId);
            return;
        }

        if (resultCode != 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setResultDescription(truncate(resultDesc));
            paymentRepository.save(payment);

            auditService.record(AuditAction.MPESA_PAYMENT_FAILED, "INVOICE", payment.getInvoice().getId(),
                "M-Pesa payment " + payment.getId() + " failed (code " + resultCode + "): "
                    + shorten(resultDesc, 200));
            return;
        }

        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Successful M-Pesa callback is missing the receipt number");
        }
        if (paymentRepository.existsByMpesaReceiptNumber(receiptNumber)) {
            log.warn("M-Pesa receipt {} was already recorded", receiptNumber);
            return;
        }

        Invoice invoice = invoiceRepository.findByIdForUpdate(payment.getInvoice().getId())
            .orElseThrow(() -> new IllegalStateException("Invoice missing for payment " + payment.getId()));

        BigDecimal balance = invoice.getTotalAmount().subtract(invoice.getAmountPaid());
        boolean amountMatches = paidAmount != null && paidAmount.compareTo(payment.getAmount()) == 0;
        boolean fitsBalance = balance.signum() > 0 && payment.getAmount().compareTo(balance) <= 0;

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setMpesaReceiptNumber(receiptNumber);
        payment.setPaidAt(Instant.now());

        boolean applied = false;
        if (amountMatches && fitsBalance) {
            applyToInvoice(invoice, payment.getAmount());
            payment.setResultDescription(truncate(resultDesc));
            applied = true;
        } else {
            // Money was received, so we record it, but we don't touch the invoice automatically.
            log.warn("M-Pesa payment {} (receipt {}) needs manual reconciliation", payment.getId(), receiptNumber);
            payment.setResultDescription("RECEIVED BUT NOT APPLIED - needs manual reconciliation");
        }

        Payment saved = paymentRepository.save(payment);

        if (applied) {
            BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getAmountPaid());
            auditService.record(AuditAction.MPESA_PAYMENT_CONFIRMED, "INVOICE", invoice.getId(),
                "M-Pesa payment " + saved.getId() + " of " + saved.getAmount().toPlainString()
                    + " confirmed (receipt " + receiptNumber + "); invoice now " + invoice.getStatus()
                    + ", balance " + remaining.toPlainString());

            // Only confirm to the tenant when the money actually reached their invoice.
            tenantNotifier.paymentReceived(saved, invoice);
        } else {
            auditService.record(AuditAction.MPESA_NEEDS_RECONCILIATION, "INVOICE", invoice.getId(),
                "M-Pesa receipt " + receiptNumber + ": " + paidAmount + " received (requested "
                    + payment.getAmount().toPlainString() + ") but not applied; needs manual reconciliation");
        }
    }

    public List<Payment> getByInvoice(UUID invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    private void applyToInvoice(Invoice invoice, BigDecimal amount) {
        BigDecimal balance = invoice.getTotalAmount().subtract(invoice.getAmountPaid());

        if (balance.signum() <= 0) {
            throw new IllegalArgumentException("This invoice is already fully paid");
        }
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                "Payment of " + amount + " exceeds the outstanding balance of " + balance);
        }

        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));

        if (invoice.getAmountPaid().compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        invoiceRepository.save(invoice);
    }

    private static String truncate(String text) {
        return shorten(text, 250);
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}