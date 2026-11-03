package com.kasa.estate_management.notification.provider;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.email.provider", havingValue = "smtp")
public class SmtpEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String company;

    public SmtpEmailProvider(ObjectProvider<JavaMailSender> mailSenderProvider,
                             @Value("${spring.mail.username:}") String username,
                             @Value("${notifications.email.from:}") String configuredFrom,
                             @Value("${app.company-name:Kasa Estate}") String company) {
        // Fail at startup, not on every send, if SMTP is chosen without being configured.
        this.mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException(
                "SPRING_MAIL_HOST must be set when notifications.email.provider=smtp");
        }

        this.from = configuredFrom.isBlank() ? username : configuredFrom;
        if (from.isBlank()) {
            throw new IllegalStateException(
                "EMAIL_FROM or SPRING_MAIL_USERNAME must be set when notifications.email.provider=smtp");
        }

        this.company = company;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        String subject = (notification.getSubject() != null && !notification.getSubject().isBlank())
            ? notification.getSubject()
            : "Message from " + company;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipient());
        message.setSubject(subject);
        message.setText(notification.getBody());

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // The root cause (for example "535 Authentication failed") is what makes this debuggable.
            // It is stored in the outbox row, not written to the log, because it can contain addresses.
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            String detail = (root.getMessage() != null) ? root.getMessage().replaceAll("\\s+", " ") : "";
            String reason = e.getClass().getSimpleName() + (detail.isEmpty() ? "" : ": " + detail);
            throw new NotificationDeliveryException(
                reason.length() > 200 ? reason.substring(0, 200) : reason, e);
        }

        log.info("Email sent for notification {}", notification.getId());
    }
}