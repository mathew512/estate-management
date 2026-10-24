package com.kasa.estate_management.notification.provider;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;

public interface NotificationProvider {

    NotificationChannel channel();

    /** Delivers the message, or throws if the provider could not accept it. */
    void send(Notification notification);
}