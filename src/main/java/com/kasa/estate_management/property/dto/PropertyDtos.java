package com.kasa.estate_management.property.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class PropertyDtos {

    public record CreatePropertyRequest(
        @NotBlank(message = "Property name is required") String name,
        @NotBlank(message = "Location is required") String location
    ) {}

    public record PropertyResponse(
        UUID id,
        String name,
        String location,
        Instant createdAt
    ) {}
}