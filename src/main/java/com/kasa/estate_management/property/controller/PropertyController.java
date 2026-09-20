package com.kasa.estate_management.property.controller;

import com.kasa.estate_management.property.dto.PropertyDtos.CreatePropertyRequest;
import com.kasa.estate_management.property.dto.PropertyDtos.PropertyResponse;
import com.kasa.estate_management.property.model.Property;
import com.kasa.estate_management.property.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody CreatePropertyRequest request) {
        Property property = propertyService.createProperty(request.name(), request.location());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(property));
    }

    @GetMapping("/{id}")
    public PropertyResponse getById(@PathVariable UUID id) {
        return toResponse(propertyService.getById(id));
    }

    @GetMapping
    public List<PropertyResponse> getAll() {
        return propertyService.getAll().stream().map(this::toResponse).toList();
    }

    private PropertyResponse toResponse(Property property) {
        return new PropertyResponse(
            property.getId(),
            property.getName(),
            property.getLocation(),
            property.getCreatedAt()
        );
    }
}