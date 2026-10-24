package com.kasa.estate_management.notification.service;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;
import com.kasa.estate_management.notification.provider.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationOutboxService outbox;
    private final Map<NotificationChannel, NotificationProvider> providers;

    public NotificationDispatcher(NotificationOutboxService outbox, List<NotificationProvider> providerList) {
        this.outbox = outbox;
        this.providers = providerList.stream()
            .collect(Collectors.toMap(NotificationProvider::channel, Function.identity()));
    }

    // fixedDelay: the next poll starts only after the previous one has finished.
    @Scheduled(fixedDelayString = "${notifications.poll-interval-ms:10000}")
    public void dispatchDue() {
        List<Notification> batch = outbox.claimBatch();

        for (Notification n : batch) {
            try {
                deliver(n);
            } catch (Exception e) {
                log.error("Unexpected error while handling notification {}", n.getId(), e);
            }
        }
    }

    private void deliver(Notification n) {
        NotificationProvider provider = providers.get(n.getChannel());
        if (provider == null) {
            outbox.markFailed(n.getId(), "No provider configured for " + n.getChannel());
            return;
        }

        try {
            provider.send(n);
        } catch (Exception e) {
            String reason = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Notification {} attempt {} failed: {}", n.getId(), n.getAttempts(), reason);
            outbox.markFailed(n.getId(), reason);
            return;
        }

        outbox.markSent(n.getId());
    }
}