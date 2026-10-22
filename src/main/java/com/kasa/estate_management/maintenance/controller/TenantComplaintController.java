package com.kasa.estate_management.maintenance.controller;

import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.maintenance.dto.TenantComplaintDtos.CreateMyComplaintRequest;
import com.kasa.estate_management.maintenance.dto.TenantComplaintDtos.MyComplaintResponse;
import com.kasa.estate_management.maintenance.model.Complaint;
import com.kasa.estate_management.maintenance.service.TenantComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/complaints")
public class TenantComplaintController {

    private final TenantComplaintService tenantComplaintService;

    public TenantComplaintController(TenantComplaintService tenantComplaintService) {
        this.tenantComplaintService = tenantComplaintService;
    }

    @PostMapping
    public ResponseEntity<MyComplaintResponse> create(@Valid @RequestBody CreateMyComplaintRequest request,
                                                      @AuthenticationPrincipal User currentUser) {
        Complaint complaint = tenantComplaintService.create(
            currentUser, request.unitId(), request.category(), request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(complaint));
    }

    @GetMapping
    public List<MyComplaintResponse> list(@AuthenticationPrincipal User currentUser) {
        return tenantComplaintService.list(currentUser).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public MyComplaintResponse get(@PathVariable("id") UUID id,
                                   @AuthenticationPrincipal User currentUser) {
        return toResponse(tenantComplaintService.get(currentUser, id));
    }

    private MyComplaintResponse toResponse(Complaint c) {
        return new MyComplaintResponse(
            c.getId(),
            c.getUnit().getUnitNumber(),
            c.getCategory(),
            c.getTitle(),
            c.getDescription(),
            c.getStatus(),
            c.getResolutionNotes(),
            c.getResolvedAt(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}