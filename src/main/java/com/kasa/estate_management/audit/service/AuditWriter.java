package com.kasa.estate_management.audit.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.model.AuditLog;
import com.kasa.estate_management.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class AuditWriter {

    private final AuditLogRepository repository;

    public AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction: if the business change rolls back, so does this entry. */
    @Transactional
    public void write(UUID actorId, String actorLabel, String actorRole, AuditAction action,
                      String entityType, UUID entityId, String summary) {
        save(actorId, actorLabel, actorRole, action, entityType, entityId, summary);
    }

    /** Commits on its own, so the entry survives even if the caller's request later fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(UUID actorId, String actorLabel, String actorRole, AuditAction action,
                                      String entityType, UUID entityId, String summary) {
        save(actorId, actorLabel, actorRole, action, entityType, entityId, summary);
    }

    private void save(UUID actorId, String actorLabel, String actorRole, AuditAction action,
                      String entityType, UUID entityId, String summary) {
        String label = clean(actorLabel);
        repository.save(AuditLog.builder()
            .actorId(actorId)
            .actorLabel(truncate(label.isBlank() ? "unknown" : label, 150))
            .actorRole(actorRole)
            .action(action.name())
            .entityType(truncate(clean(entityType), 40))
            .entityId(entityId == null ? null : entityId.toString())
            .summary(truncate(clean(summary), 500))
            .build());
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\p{Cntrl}", " ").trim();
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}