package com.kasa.estate_management.notification.provider;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LogSmsProvider.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification) {
        // Test hook so retries can be exercised without a real provider.
        if (notification.getBody().contains("[SIMULATE_FAILURE]")) {
            throw new NotificationDeliveryException("Simulated provider failure");
        }
        log.info("[SMS -> {}] {}", notification.getRecipient(), notification.getBody());
    }
}