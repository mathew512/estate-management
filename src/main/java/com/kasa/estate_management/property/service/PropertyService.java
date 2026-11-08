package com.kasa.estate_management.property.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.property.model.Property;
import com.kasa.estate_management.property.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final AuditService auditService;

    public PropertyService(PropertyRepository propertyRepository, AuditService auditService) {
        this.propertyRepository = propertyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Property createProperty(String name, String location) {
        Property property = Property.builder()
            .name(name)
            .location(location)
            .build();

        Property saved = propertyRepository.save(property);
        auditService.record(AuditAction.PROPERTY_CREATED, "PROPERTY", saved.getId(),
            "Created property \"" + name + "\" in " + location);
        return saved;
    }

    public Property getById(UUID id) {
        return propertyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Property not found: " + id));
    }

    public List<Property> getAll() {
        return propertyRepository.findAll();
    }
}