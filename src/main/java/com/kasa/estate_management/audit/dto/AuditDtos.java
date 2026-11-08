package com.kasa.estate_management.audit.dto;

import java.time.Instant;
import java.util.UUID;

public class AuditDtos {

    public record AuditEntryResponse(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String actor,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        String summary
    ) {}
}