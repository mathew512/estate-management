package com.kasa.estate_management.notification.service;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationStatus;
import com.kasa.estate_management.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationOutboxService {

    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final long[] RETRY_DELAY_MINUTES = {1, 5, 15, 60};

    private final NotificationRepository notificationRepository;
    private final int maxAttempts;
    private final int batchSize;

    public NotificationOutboxService(NotificationRepository notificationRepository,
                                     @Value("${notifications.max-attempts:5}") int maxAttempts,
                                     @Value("${notifications.batch-size:20}") int batchSize) {
        this.notificationRepository = notificationRepository;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    /**
     * Claims a batch of due messages in one short transaction. Each claimed row has its attempt
     * counted and its next_attempt_at pushed past the lease, so nobody else will pick it up
     * while we are calling the provider.
     */
    @Transactional
    public List<Notification> claimBatch() {
        Instant now = Instant.now();
        List<Notification> due = notificationRepository.claimDue(now, batchSize);

        for (Notification n : due) {
            n.setAttempts(n.getAttempts() + 1);
            n.setNextAttemptAt(now.plus(LEASE));
        }
        return notificationRepository.saveAll(due);
    }

    @Transactional
    public void markSent(UUID id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(Instant.now());
            n.setLastError(null);
        });
    }

    @Transactional
    public void markFailed(UUID id, String error) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setLastError(truncate(error));

            if (n.getAttempts() >= maxAttempts) {
                n.setStatus(NotificationStatus.FAILED);
            } else {
                int index = Math.min(n.getAttempts() - 1, RETRY_DELAY_MINUTES.length - 1);
                n.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(RETRY_DELAY_MINUTES[index])));
            }
        });
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 250 ? text.substring(0, 250) : text;
    }
}