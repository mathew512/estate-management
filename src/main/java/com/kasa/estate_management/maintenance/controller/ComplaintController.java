package com.kasa.estate_management.maintenance.controller;

import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.maintenance.dto.ComplaintDtos.AssignComplaintRequest;
import com.kasa.estate_management.maintenance.dto.ComplaintDtos.ComplaintResponse;
import com.kasa.estate_management.maintenance.dto.ComplaintDtos.CreateComplaintRequest;
import com.kasa.estate_management.maintenance.dto.ComplaintDtos.UpdateComplaintStatusRequest;
import com.kasa.estate_management.maintenance.model.Complaint;
import com.kasa.estate_management.maintenance.model.ComplaintStatus;
import com.kasa.estate_management.maintenance.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping
    public ResponseEntity<ComplaintResponse> create(@Valid @RequestBody CreateComplaintRequest request,
                                                    @AuthenticationPrincipal User currentUser) {
        Complaint complaint = complaintService.createComplaint(
            request.unitId(), request.tenantId(), request.category(),
            request.title(), request.description(), request.priority(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(complaint));
    }

    /** Filters: ?unitId= takes precedence over ?status=; with neither, returns everything. */
    @GetMapping
    public List<ComplaintResponse> list(
        @RequestParam(name = "status", required = false) ComplaintStatus status,
        @RequestParam(name = "unitId", required = false) UUID unitId
    ) {
        List<Complaint> complaints;
        if (unitId != null) {
            complaints = complaintService.getByUnit(unitId);
        } else if (status != null) {
            complaints = complaintService.getByStatus(status);
        } else {
            complaints = complaintService.getAll();
        }
        return complaints.stream().map(this::toResponse).toList();
    }

    @GetMapping("/mine")
    public List<ComplaintResponse> mine(@AuthenticationPrincipal User currentUser) {
        return complaintService.getByAssignee(currentUser.getId()).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ComplaintResponse getById(@PathVariable("id") UUID id) {
        return toResponse(complaintService.getById(id));
    }

    @PatchMapping("/{id}/assign")
    public ComplaintResponse assign(@PathVariable("id") UUID id,
                                    @Valid @RequestBody AssignComplaintRequest request) {
        return toResponse(complaintService.assign(id, request.staffUserId()));
    }

    @PatchMapping("/{id}/status")
    public ComplaintResponse updateStatus(@PathVariable("id") UUID id,
                                          @Valid @RequestBody UpdateComplaintStatusRequest request,
                                          @AuthenticationPrincipal User currentUser) {
        return toResponse(complaintService.updateStatus(id, request.status(), request.notes(), currentUser));
    }

    private ComplaintResponse toResponse(Complaint c) {
        return new ComplaintResponse(
            c.getId(),
            c.getUnit().getId(),
            c.getUnit().getUnitNumber(),
            c.getTenant().getId(),
            c.getTenant().getFullName(),
            c.getCategory(),
            c.getTitle(),
            c.getDescription(),
            c.getPriority(),
            c.getStatus(),
            c.getAssignedTo() != null ? c.getAssignedTo().getId() : null,
            c.getAssignedTo() != null ? c.getAssignedTo().getFullName() : null,
            c.getResolutionNotes(),
            c.getResolvedAt(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}