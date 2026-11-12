package com.kasa.estate_management.payment;

import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.model.InvoiceStatus;
import com.kasa.estate_management.billing.repository.InvoiceRepository;
import com.kasa.estate_management.billing.service.InvoiceService;
import com.kasa.estate_management.payment.model.Payment;
import com.kasa.estate_management.payment.model.PaymentMethod;
import com.kasa.estate_management.payment.model.PaymentStatus;
import com.kasa.estate_management.payment.repository.PaymentRepository;
import com.kasa.estate_management.payment.service.PaymentService;
import com.kasa.estate_management.property.model.Property;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.model.UnitStatus;
import com.kasa.estate_management.property.repository.UnitRepository;
import com.kasa.estate_management.property.service.PropertyService;
import com.kasa.estate_management.property.service.UnitService;
import com.kasa.estate_management.support.IntegrationTestBase;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.service.LeaseService;
import com.kasa.estate_management.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every test builds its own property, unit, tenant and lease with unique values, so tests
 * never depend on each other or on the order they run in.
 */
class MoneyRulesTest extends IntegrationTestBase {

    private static final LocalDate NOV = LocalDate.of(2026, 11, 1);

    @Autowired private PropertyService propertyService;
    @Autowired private UnitService unitService;
    @Autowired private TenantService tenantService;
    @Autowired private LeaseService leaseService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private DataSource dataSource;

    private record Fixture(UUID unitId, UUID leaseId) {}

    // ---------------------------------------------------------------- leases

    @Test
    void oneActiveLeasePerUnitAndAFreedUnitCanBeLeasedAgain() {
        Fixture f = newLease("10000");
        UUID otherTenant = newTenant();

        assertThatThrownBy(() -> leaseService.createLease(
            f.unitId(), otherTenant, NOV, new BigDecimal("10000"), BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has an active lease");

        leaseService.endLease(f.leaseId());

        Lease second = leaseService.createLease(
            f.unitId(), otherTenant, NOV, new BigDecimal("10000"), BigDecimal.ZERO);
        assertThat(second.getId()).isNotEqualTo(f.leaseId());
    }

    @Test
    void leaseLifecycleKeepsTheUnitStatusInSync() {
        Fixture f = newLease("10000");
        assertThat(unitStatus(f.unitId())).isEqualTo(UnitStatus.OCCUPIED);

        leaseService.endLease(f.leaseId());
        assertThat(unitStatus(f.unitId())).isEqualTo(UnitStatus.VACANT);
    }

    // -------------------------------------------------------------- invoices

    @Test
    void invoiceCopiesTheLeaseRentAndNormalisesTheMonth() {
        Fixture f = newLease("10000");

        Invoice created = invoiceService.generateInvoice(
            f.leaseId(), LocalDate.of(2026, 11, 17), new BigDecimal("500"));

        Invoice invoice = reload(created.getId());
        assertThat(invoice.getPeriodMonth()).isEqualTo(NOV);
        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(invoice.getRentAmount()).isEqualByComparingTo("10000");
        assertThat(invoice.getUtilityAmount()).isEqualByComparingTo("500");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("10500");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void aSecondInvoiceForTheSameMonthIsRejected() {
        Fixture f = newLease("10000");
        invoiceService.generateInvoice(f.leaseId(), NOV, null);

        assertThatThrownBy(() -> invoiceService.generateInvoice(f.leaseId(), LocalDate.of(2026, 11, 20), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void anEndedLeaseCannotBeInvoiced() {
        Fixture f = newLease("10000");
        leaseService.endLease(f.leaseId());

        assertThatThrownBy(() -> invoiceService.generateInvoice(f.leaseId(), NOV, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-active");
    }

    @Test
    void aPenaltyRaisesTheInvoiceTotal() {
        UUID invoiceId = newInvoiceId("10000");

        invoiceService.applyPenalty(invoiceId, new BigDecimal("500"));

        assertThat(reload(invoiceId).getTotalAmount()).isEqualByComparingTo("10500");
    }

    // ------------------------------------------------------ manual payments

    @Test
    void partThenFullPaymentMovesTheStatusAndTracksTheAmount() {
        UUID invoiceId = newInvoiceId("10000");

        paymentService.recordManualPayment(invoiceId, new BigDecimal("4000"), PaymentMethod.CASH, "R1");
        Invoice afterPart = reload(invoiceId);
        assertThat(afterPart.getAmountPaid()).isEqualByComparingTo("4000");
        assertThat(afterPart.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);

        paymentService.recordManualPayment(invoiceId, new BigDecimal("6000"), PaymentMethod.BANK, "R2");
        Invoice afterFull = reload(invoiceId);
        assertThat(afterFull.getAmountPaid()).isEqualByComparingTo("10000");
        assertThat(afterFull.getStatus()).isEqualTo(InvoiceStatus.PAID);

        assertThat(paymentRepository.findByInvoiceId(invoiceId)).hasSize(2);
        assertThat(auditCount(invoiceId, "PAYMENT_RECORDED")).isEqualTo(2);
    }

    @Test
    void anOverpaymentIsRejectedAndChangesNothing() {
        UUID invoiceId = newInvoiceId("10000");

        assertThatThrownBy(() -> paymentService.recordManualPayment(
            invoiceId, new BigDecimal("15000"), PaymentMethod.CASH, "TOO-MUCH"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds the outstanding balance");

        Invoice invoice = reload(invoiceId);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(paymentRepository.findByInvoiceId(invoiceId)).isEmpty();
        // A refused change must leave no audit entry behind either.
        assertThat(auditCount(invoiceId, "PAYMENT_RECORDED")).isZero();
    }

    @Test
    void aPaidInvoiceCannotBePaidAgain() {
        UUID invoiceId = newInvoiceId("10000");
        paymentService.recordManualPayment(invoiceId, new BigDecimal("10000"), PaymentMethod.CASH, "FULL");

        assertThatThrownBy(() -> paymentService.recordManualPayment(
            invoiceId, new BigDecimal("1"), PaymentMethod.CASH, "AGAIN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already fully paid");
    }

    @Test
    void mpesaCannotBeRecordedAsAManualPayment() {
        UUID invoiceId = newInvoiceId("10000");

        assertThatThrownBy(() -> paymentService.recordManualPayment(
            invoiceId, new BigDecimal("100"), PaymentMethod.MPESA, "FAKE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("M-Pesa payments are recorded automatically");

        assertThat(paymentRepository.findByInvoiceId(invoiceId)).isEmpty();
    }

    @Test
    void anOverdueInvoiceStaysOverdueUntilPaidInFull() {
        UUID invoiceId = newInvoiceId("10000");
        invoiceService.markOverdue(invoiceId);

        paymentService.recordManualPayment(invoiceId, new BigDecimal("1000"), PaymentMethod.CASH, "P1");
        assertThat(reload(invoiceId).getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        paymentService.recordManualPayment(invoiceId, new BigDecimal("9000"), PaymentMethod.CASH, "P2");
        assertThat(reload(invoiceId).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    // ------------------------------------------------------ M-Pesa callbacks

    @Test
    void aSuccessfulMpesaCallbackAppliesThePaymentExactlyOnce() {
        UUID invoiceId = newInvoiceId("10000");
        String checkoutId = pendingMpesaPayment(invoiceId, "4000");
        String receipt = "RCPT" + shortId();

        paymentService.handleMpesaCallback(checkoutId, 0, "ok", new BigDecimal("4000"), receipt);
        // Safaricom retries callbacks, so the same one arriving again must change nothing.
        paymentService.handleMpesaCallback(checkoutId, 0, "ok", new BigDecimal("4000"), receipt);

        Invoice invoice = reload(invoiceId);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("4000");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);

        Payment payment = paymentByCheckoutId(checkoutId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getMpesaReceiptNumber()).isEqualTo(receipt);
        assertThat(auditCount(invoiceId, "MPESA_PAYMENT_CONFIRMED")).isEqualTo(1);
    }

    @Test
    void aCallbackWithADifferentAmountIsRecordedButNotApplied() {
        UUID invoiceId = newInvoiceId("10000");
        String checkoutId = pendingMpesaPayment(invoiceId, "4000");

        paymentService.handleMpesaCallback(
            checkoutId, 0, "ok", new BigDecimal("3000"), "RCPT" + shortId());

        Payment payment = paymentByCheckoutId(checkoutId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getResultDescription()).contains("RECEIVED BUT NOT APPLIED");

        Invoice invoice = reload(invoiceId);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(auditCount(invoiceId, "MPESA_NEEDS_RECONCILIATION")).isEqualTo(1);
    }

    @Test
    void aFailedMpesaCallbackMarksThePaymentFailedAndLeavesTheInvoiceAlone() {
        UUID invoiceId = newInvoiceId("10000");
        String checkoutId = pendingMpesaPayment(invoiceId, "4000");

        paymentService.handleMpesaCallback(checkoutId, 1032, "Request cancelled by user", null, null);

        assertThat(paymentByCheckoutId(checkoutId).getStatus()).isEqualTo(PaymentStatus.FAILED);
        Invoice invoice = reload(invoiceId);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void aReusedReceiptNumberIsNotCreditedTwice() {
        UUID invoiceId = newInvoiceId("10000");
        String first = pendingMpesaPayment(invoiceId, "3000");
        String second = pendingMpesaPayment(invoiceId, "3000");
        String receipt = "RCPT" + shortId();

        paymentService.handleMpesaCallback(first, 0, "ok", new BigDecimal("3000"), receipt);
        paymentService.handleMpesaCallback(second, 0, "ok", new BigDecimal("3000"), receipt);

        assertThat(reload(invoiceId).getAmountPaid()).isEqualByComparingTo("3000");
        assertThat(paymentByCheckoutId(second).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ----------------------------------------------------------- concurrency

    @Test
    void twoSimultaneousPaymentsCannotOverpayAnInvoice() throws Exception {
        UUID invoiceId = newInvoiceId("10000");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> pay = () -> {
            ready.countDown();
            go.await();
            try {
                paymentService.recordManualPayment(
                    invoiceId, new BigDecimal("6000"), PaymentMethod.CASH, "RACE");
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(pay);
            Future<Boolean> b = pool.submit(pay);
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            boolean firstOk = a.get(30, TimeUnit.SECONDS);
            boolean secondOk = b.get(30, TimeUnit.SECONDS);

            // 6000 + 6000 > 10000, so exactly one of the two may succeed.
            assertThat(List.of(firstOk, secondOk)).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(reload(invoiceId).getAmountPaid()).isEqualByComparingTo("6000");
        assertThat(paymentRepository.findByInvoiceId(invoiceId)).hasSize(1);
    }

    // --------------------------------------------------------------- helpers

    private Fixture newLease(String rent) {
        Property property = propertyService.createProperty("Test Plaza " + UUID.randomUUID(), "Nairobi");
        Unit unit = unitService.addUnit(property.getId(), "U1", new BigDecimal("12000"), null);
        UUID tenantId = newTenant();
        Lease lease = leaseService.createLease(
            unit.getId(), tenantId, NOV, new BigDecimal(rent), new BigDecimal(rent));
        return new Fixture(unit.getId(), lease.getId());
    }

    private UUID newTenant() {
        Tenant tenant = tenantService.onboardTenant("Test Tenant", randomPhone(), null, null);
        return tenant.getId();
    }

    private UUID newInvoiceId(String rent) {
        Fixture f = newLease(rent);
        return invoiceService.generateInvoice(f.leaseId(), NOV, null).getId();
    }

    /** A pending M-Pesa payment, as if an STK Push had been sent (we cannot call Safaricom from a test). */
    private String pendingMpesaPayment(UUID invoiceId, String amount) {
        Invoice invoice = reload(invoiceId);
        String checkoutId = "ws_CO_TEST_" + UUID.randomUUID();

        paymentRepository.save(Payment.builder()
            .invoice(invoice)
            .amount(new BigDecimal(amount))
            .method(PaymentMethod.MPESA)
            .status(PaymentStatus.PENDING)
            .phoneNumber("0712345678")
            .checkoutRequestId(checkoutId)
            .build());
        return checkoutId;
    }

    private Invoice reload(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow();
    }

    private Payment paymentByCheckoutId(String checkoutId) {
        return paymentRepository.findByCheckoutRequestId(checkoutId).orElseThrow();
    }

    private UnitStatus unitStatus(UUID unitId) {
        return unitRepository.findById(unitId).orElseThrow().getStatus();
    }

    private long auditCount(UUID entityId, String action) {
        return JdbcClient.create(dataSource)
            .sql("SELECT count(*) FROM audit_log WHERE entity_id = :id AND action = :action")
            .param("id", entityId.toString())
            .param("action", action)
            .query(Long.class)
            .single();
    }

    private static String randomPhone() {
        return "07" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}