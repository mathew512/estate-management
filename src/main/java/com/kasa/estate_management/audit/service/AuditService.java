package com.kasa.estate_management.audit.service;

import com.kasa.estate_management.audit.dto.AuditDtos.AuditEntryResponse;
import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.auth.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final int MAX_LIMIT = 500;

    private record Actor(UUID id, String label, String role) {}

    private final AuditWriter writer;
    private final JdbcClient jdbc;

    public AuditService(AuditWriter writer, DataSource dataSource) {
        this.writer = writer;
        this.jdbc = JdbcClient.create(dataSource);
    }

    /**
     * Records an event for the current user (or SYSTEM when nobody is logged in), inside the
     * caller's transaction. It fails the caller on purpose: no unaudited business changes.
     */
    public void record(AuditAction action, String entityType, UUID entityId, String summary) {
        Actor actor = currentActor();
        writer.write(actor.id(), actor.label(), actor.role(), action, entityType, entityId, summary);
    }

    /** Own transaction, never throws. For events where an audit problem must not break the request. */
    public void recordBestEffort(AuditAction action, String entityType, UUID entityId, String summary) {
        Actor actor = currentActor();
        recordBestEffortAs(actor.label(), actor.id(), actor.role(), action, entityType, entityId, summary);
    }

    /** Own transaction, never throws, with an explicit actor (used for logins, where nobody is authenticated yet). */
    public void recordBestEffortAs(String actorLabel, UUID actorId, String actorRole, AuditAction action,
                                   String entityType, UUID entityId, String summary) {
        try {
            writer.writeInNewTransaction(actorId, actorLabel, actorRole, action, entityType, entityId, summary);
        } catch (RuntimeException e) {
            log.error("Could not write audit entry {}", action, e);
        }
    }

    public List<AuditEntryResponse> search(String entityType, String entityId, UUID actorId, String action,
                                           LocalDate from, LocalDate to, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, occurred_at, actor_id, actor_label, actor_role, action, entity_type, entity_id, summary
            FROM audit_log WHERE 1 = 1""");
        Map<String, Object> params = new HashMap<>();

        if (hasText(entityType)) {
            sql.append(" AND entity_type = :entityType");
            params.put("entityType", entityType.trim().toUpperCase());
        }
        if (hasText(entityId)) {
            sql.append(" AND entity_id = :entityId");
            params.put("entityId", entityId.trim());
        }
        if (actorId != null) {
            sql.append(" AND actor_id = :actorId");
            params.put("actorId", actorId);
        }
        if (hasText(action)) {
            sql.append(" AND action = :action");
            params.put("action", action.trim().toUpperCase());
        }
        if (from != null) {
            sql.append(" AND occurred_at >= :from");
            params.put("from", Timestamp.from(from.atStartOfDay(NAIROBI).toInstant()));
        }
        if (to != null) {
            sql.append(" AND occurred_at < :to");
            params.put("to", Timestamp.from(to.plusDays(1).atStartOfDay(NAIROBI).toInstant()));
        }

        sql.append(" ORDER BY occurred_at DESC LIMIT :limit");
        params.put("limit", Math.max(1, Math.min(limit, MAX_LIMIT)));

        return jdbc.sql(sql.toString())
            .params(params)
            .query((rs, rowNum) -> new AuditEntryResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_label"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("summary")))
            .list();
    }

    private Actor currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return new Actor(user.getId(), user.getEmail(), user.getRole().name());
        }
        return new Actor(null, "SYSTEM", null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}