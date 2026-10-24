package com.kasa.estate_management.notification.service;

import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.maintenance.model.Complaint;
import com.kasa.estate_management.maintenance.model.ComplaintStatus;
import com.kasa.estate_management.notification.model.NotificationType;
import com.kasa.estate_management.payment.model.Payment;
import com.kasa.estate_management.tenant.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns business events into queued SMS messages for the tenant. It runs inside the caller's
 * transaction, so a rolled-back change never leaves a message behind. It never throws.
 */
@Component
public class TenantNotifier {

    private static final Logger log = LoggerFactory.getLogger(TenantNotifier.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final NotificationService notificationService;
    private final String company;

    public TenantNotifier(NotificationService notificationService,
                          @Value("${app.company-name:Kasa Estate}") String company) {
        this.notificationService = notificationService;
        this.company = company;
    }

    public void invoiceCreated(Invoice invoice) {
        try {
            Tenant tenant = invoice.getLease().getTenant();

            StringBuilder sb = new StringBuilder();
            sb.append("Hi ").append(firstName(tenant)).append(", your rent invoice for ")
                .append(MONTH.format(invoice.getPeriodMonth()))
                .append(" is KSh ").append(money(total(invoice)));
            if (invoice.getUtilityAmount().signum() > 0) {
                sb.append(" (rent ").append(money(invoice.getRentAmount()))
                    .append(" + utilities ").append(money(invoice.getUtilityAmount())).append(")");
            }
            sb.append(". Due ").append(DAY.format(invoice.getDueDate()))
                .append(". Pay via M-Pesa. - ").append(company);

            queue(tenant, sb.toString(), NotificationType.INVOICE_CREATED, "INVOICE_CREATED:" + invoice.getId());
        } catch (RuntimeException e) {
            log.warn("Could not queue invoice notification", e);
        }
    }

    public void paymentReceived(Payment payment, Invoice invoice) {
        try {
            Tenant tenant = invoice.getLease().getTenant();
            BigDecimal balance = total(invoice).subtract(invoice.getAmountPaid());

            String ref = payment.getMpesaReceiptNumber() != null
                ? payment.getMpesaReceiptNumber() : payment.getReference();

            StringBuilder sb = new StringBuilder();
            sb.append("Payment of KSh ").append(money(payment.getAmount()))
                .append(" received for ").append(MONTH.format(invoice.getPeriodMonth())).append(" rent.");
            if (ref != null && !ref.isBlank()) {
                sb.append(" Ref ").append(ref).append(".");
            }
            sb.append(balance.signum() > 0
                ? " Balance KSh " + money(balance) + "."
                : " Invoice fully paid.");
            sb.append(" Thank you. - ").append(company);

            queue(tenant, sb.toString(), NotificationType.PAYMENT_RECEIVED, "PAYMENT_RECEIVED:" + payment.getId());
        } catch (RuntimeException e) {
            log.warn("Could not queue payment notification", e);
        }
    }

    public void invoiceOverdue(Invoice invoice) {
        try {
            Tenant tenant = invoice.getLease().getTenant();
            BigDecimal balance = total(invoice).subtract(invoice.getAmountPaid());

            String message = "Reminder: your rent invoice for " + MONTH.format(invoice.getPeriodMonth())
                + " was due on " + DAY.format(invoice.getDueDate()) + " and is overdue. Balance KSh "
                + money(balance) + ". Please pay as soon as possible. - " + company;

            queue(tenant, message, NotificationType.INVOICE_OVERDUE, "INVOICE_OVERDUE:" + invoice.getId());
        } catch (RuntimeException e) {
            log.warn("Could not queue overdue notification", e);
        }
    }

    public void complaintUpdated(Complaint complaint, ComplaintStatus status) {
        try {
            String title = shorten(complaint.getTitle(), 40);

            String text = switch (status) {
                case IN_PROGRESS -> "it has been assigned and work is under way.";
                case RESOLVED -> "it has been marked resolved. If the problem continues, please let us know.";
                default -> null;
            };
            if (text == null) {
                return;
            }

            String message = "Update on your request \"" + title + "\": " + text + " - " + company;
            queue(complaint.getTenant(), message, NotificationType.COMPLAINT_UPDATED,
                "COMPLAINT_UPDATED:" + complaint.getId() + ":" + status);
        } catch (RuntimeException e) {
            log.warn("Could not queue complaint notification", e);
        }
    }

    private void queue(Tenant tenant, String message, NotificationType type, String key) {
        notificationService.queueSms(tenant.getPhoneNumber(), message, type, key + ":SMS");
    }

    private static BigDecimal total(Invoice invoice) {
        return invoice.getRentAmount().add(invoice.getUtilityAmount()).add(invoice.getPenaltyAmount());
    }

    private static String firstName(Tenant tenant) {
        String name = tenant.getFullName().trim();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    private static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String text = format.format(amount);
        return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
    }

    private static String shorten(String text, int max) {
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }
}