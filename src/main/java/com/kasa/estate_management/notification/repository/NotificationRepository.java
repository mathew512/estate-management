package com.kasa.estate_management.notification.repository;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByOrderByCreatedAtDesc();

    List<Notification> findTop50ByStatusOrderByCreatedAtDesc(NotificationStatus status);

    /**
     * Inserts a message unless one with the same dedupe key already exists. Returns 1 if a row
     * was inserted, 0 if it was a duplicate. The CASTs let Postgres type the nullable parameters.
     */
    @Modifying
    @Query(value = """
        INSERT INTO notifications
            (channel, recipient, subject, body, type, status, last_error, dedupe_key)
        VALUES
            (:channel, :recipient, CAST(:subject AS varchar), :body, :type, :status,
             CAST(:lastError AS varchar), CAST(:dedupeKey AS varchar))
        ON CONFLICT (dedupe_key) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("channel") String channel,
                       @Param("recipient") String recipient,
                       @Param("subject") String subject,
                       @Param("body") String body,
                       @Param("type") String type,
                       @Param("status") String status,
                       @Param("lastError") String lastError,
                       @Param("dedupeKey") String dedupeKey);

    /**
     * Locks up to {@code batch} due PENDING rows. Rows already locked by another instance are
     * skipped, so two app instances never claim the same message. Must run inside a transaction.
     */
    @Query(value = """
        SELECT * FROM notifications
        WHERE status = 'PENDING' AND next_attempt_at <= :now
        ORDER BY next_attempt_at
        LIMIT :batch
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Notification> claimDue(@Param("now") Instant now, @Param("batch") int batch);
}