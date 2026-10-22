package com.kasa.estate_management.maintenance.dto;

import com.kasa.estate_management.maintenance.model.ComplaintCategory;
import com.kasa.estate_management.maintenance.model.ComplaintPriority;
import com.kasa.estate_management.maintenance.model.ComplaintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class ComplaintDtos {

    public record CreateComplaintRequest(
        @NotNull(message = "Unit ID is required") UUID unitId,
        @NotNull(message = "Tenant ID is required") UUID tenantId,
        @NotNull(message = "Category is required") ComplaintCategory category,
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be 150 characters or less") String title,
        @NotBlank(message = "Description is required") String description,
        ComplaintPriority priority
    ) {}

    public record AssignComplaintRequest(
        @NotNull(message = "Staff user ID is required") UUID staffUserId
    ) {}

    public record UpdateComplaintStatusRequest(
        @NotNull(message = "Status is required") ComplaintStatus status,
        String notes
    ) {}

    public record ComplaintResponse(
        UUID id,
        UUID unitId,
        String unitNumber,
        UUID tenantId,
        String tenantName,
        ComplaintCategory category,
        String title,
        String description,
        ComplaintPriority priority,
        ComplaintStatus status,
        UUID assignedToId,
        String assignedToName,
        String resolutionNotes,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
    ) {}
}