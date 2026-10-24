package com.kasa.estate_management.notification.service;

import com.kasa.estate_management.common.PhoneNumbers;
import com.kasa.estate_management.notification.model.NotificationChannel;
import com.kasa.estate_management.notification.model.NotificationStatus;
import com.kasa.estate_management.notification.model.NotificationType;
import com.kasa.estate_management.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class NotificationService {

    public enum QueueResult {
        QUEUED,
        DUPLICATE,
        REJECTED
    }

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Queues an SMS. Joins the caller's transaction, so if the business change rolls back,
     * the message is never queued. Never throws for bad input: it records a FAILED row instead.
     */
    @Transactional
    public QueueResult queueSms(String phone, String message, NotificationType type, String dedupeKey) {
        if (message == null || message.isBlank()) {
            log.warn("Refusing to queue an empty SMS of type {}", type);
            return QueueResult.REJECTED;
        }

        String msisdn = PhoneNumbers.normalizeKenyan(phone);
        if (msisdn == null) {
            insert(NotificationChannel.SMS, phone, null, message, type,
                NotificationStatus.FAILED, "Invalid Kenyan phone number", dedupeKey);
            log.warn("SMS of type {} not queued: invalid phone number", type);
            return QueueResult.REJECTED;
        }

        int rows = insert(NotificationChannel.SMS, "+" + msisdn, null, message, type,
            NotificationStatus.PENDING, null, dedupeKey);
        return rows == 1 ? QueueResult.QUEUED : QueueResult.DUPLICATE;
    }

    @Transactional
    public QueueResult queueEmail(String email, String subject, String message,
                                  NotificationType type, String dedupeKey) {
        if (message == null || message.isBlank()) {
            log.warn("Refusing to queue an empty email of type {}", type);
            return QueueResult.REJECTED;
        }

        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            insert(NotificationChannel.EMAIL, email, subject, message, type,
                NotificationStatus.FAILED, "Invalid email address", dedupeKey);
            log.warn("Email of type {} not queued: invalid address", type);
            return QueueResult.REJECTED;
        }

        int rows = insert(NotificationChannel.EMAIL, email.trim(), subject, message, type,
            NotificationStatus.PENDING, null, dedupeKey);
        return rows == 1 ? QueueResult.QUEUED : QueueResult.DUPLICATE;
    }

    private int insert(NotificationChannel channel, String recipient, String subject, String body,
                       NotificationType type, NotificationStatus status, String error, String dedupeKey) {
        String safeRecipient = (recipient == null || recipient.isBlank()) ? "unknown" : recipient.trim();

        return notificationRepository.insertIfAbsent(
            channel.name(),
            truncate(safeRecipient, 150),
            truncate(subject, 200),
            body,
            type.name(),
            status.name(),
            truncate(error, 255),
            truncate(dedupeKey, 150)
        );
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}