package com.kasa.estate_management.audit.controller;

import com.kasa.estate_management.audit.dto.AuditDtos.AuditEntryResponse;
import com.kasa.estate_management.audit.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Newest first. All filters are optional; limit defaults to 100 and is capped at 500. */
    @GetMapping
    public List<AuditEntryResponse> search(
        @RequestParam(name = "entityType", required = false) String entityType,
        @RequestParam(name = "entityId", required = false) String entityId,
        @RequestParam(name = "actorId", required = false) UUID actorId,
        @RequestParam(name = "action", required = false) String action,
        @RequestParam(name = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(name = "limit", required = false, defaultValue = "100") int limit
    ) {
        return auditService.search(entityType, entityId, actorId, action, from, to, limit);
    }
}