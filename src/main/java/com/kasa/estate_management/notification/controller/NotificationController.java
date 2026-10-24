package com.kasa.estate_management.notification.controller;

import com.kasa.estate_management.notification.dto.NotificationDtos.NotificationResponse;
import com.kasa.estate_management.notification.dto.NotificationDtos.QueueResultResponse;
import com.kasa.estate_management.notification.dto.NotificationDtos.QueueTestRequest;
import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;
import com.kasa.estate_management.notification.model.NotificationStatus;
import com.kasa.estate_management.notification.model.NotificationType;
import com.kasa.estate_management.notification.repository.NotificationRepository;
import com.kasa.estate_management.notification.service.NotificationService;
import com.kasa.estate_management.notification.service.NotificationService.QueueResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationService notificationService,
                                  NotificationRepository notificationRepository) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @PostMapping("/test")
    public ResponseEntity<QueueResultResponse> queueTest(@Valid @RequestBody QueueTestRequest request) {
        QueueResult result = (request.channel() == NotificationChannel.SMS)
            ? notificationService.queueSms(
                request.recipient(), request.message(), NotificationType.GENERAL, request.dedupeKey())
            : notificationService.queueEmail(
                request.recipient(), request.subject(), request.message(),
                NotificationType.GENERAL, request.dedupeKey());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new QueueResultResponse(result.name()));
    }

    /** Shows the 50 most recent messages, optionally filtered by status. */
    @GetMapping
    public List<NotificationResponse> list(
        @RequestParam(name = "status", required = false) NotificationStatus status
    ) {
        List<Notification> rows = (status != null)
            ? notificationRepository.findTop50ByStatusOrderByCreatedAtDesc(status)
            : notificationRepository.findTop50ByOrderByCreatedAtDesc();
        return rows.stream().map(this::toResponse).toList();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
            n.getId(), n.getChannel(), n.getRecipient(), n.getSubject(), n.getBody(),
            n.getType(), n.getStatus(), n.getAttempts(), n.getLastError(),
            n.getNextAttemptAt(), n.getSentAt(), n.getCreatedAt()
        );
    }
}