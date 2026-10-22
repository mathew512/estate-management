package com.kasa.estate_management.maintenance.dto;

import com.kasa.estate_management.maintenance.model.ComplaintCategory;
import com.kasa.estate_management.maintenance.model.ComplaintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class TenantComplaintDtos {

    public record CreateMyComplaintRequest(
        UUID unitId,
        @NotNull(message = "Category is required") ComplaintCategory category,
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be 150 characters or less") String title,
        @NotBlank(message = "Description is required") String description
    ) {}

    public record MyComplaintResponse(
        UUID id,
        String unitNumber,
        ComplaintCategory category,
        String title,
        String description,
        ComplaintStatus status,
        String resolutionNotes,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
    ) {}
}