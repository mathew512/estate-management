package com.kasa.estate_management.notification.dto;

import com.kasa.estate_management.notification.model.NotificationChannel;
import com.kasa.estate_management.notification.model.NotificationStatus;
import com.kasa.estate_management.notification.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class NotificationDtos {

    public record QueueTestRequest(
        @NotNull(message = "Channel is required") NotificationChannel channel,
        @NotBlank(message = "Recipient is required") String recipient,
        String subject,
        @NotBlank(message = "Message is required") String message,
        String dedupeKey
    ) {}

    public record QueueResultResponse(String outcome) {}

    public record NotificationResponse(
        UUID id,
        NotificationChannel channel,
        String recipient,
        String subject,
        String body,
        NotificationType type,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant nextAttemptAt,
        Instant sentAt,
        Instant createdAt
    ) {}
}