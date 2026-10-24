package com.kasa.estate_management.notification.provider;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LogEmailProvider.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        if (notification.getBody().contains("[SIMULATE_FAILURE]")) {
            throw new NotificationDeliveryException("Simulated provider failure");
        }
        log.info("[EMAIL -> {}] subject='{}' body='{}'",
            notification.getRecipient(), notification.getSubject(), notification.getBody());
    }
}