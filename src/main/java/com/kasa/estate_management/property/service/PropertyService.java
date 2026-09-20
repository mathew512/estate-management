package com.kasa.estate_management.property.service;

import com.kasa.estate_management.property.model.Property;
import com.kasa.estate_management.property.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public Property createProperty(String name, String location) {
        Property property = Property.builder()
            .name(name)
            .location(location)
            .build();

        return propertyRepository.save(property);
    }

    public Property getById(UUID id) {
        return propertyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Property not found: " + id));
    }

    public List<Property> getAll() {
        return propertyRepository.findAll();
    }
}