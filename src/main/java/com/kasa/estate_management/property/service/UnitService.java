package com.kasa.estate_management.property.service;

import com.kasa.estate_management.property.model.Property;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.model.UnitStatus;
import com.kasa.estate_management.property.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final PropertyService propertyService;

    public UnitService(UnitRepository unitRepository, PropertyService propertyService) {
        this.unitRepository = unitRepository;
        this.propertyService = propertyService;
    }

    @Transactional
    public Unit addUnit(UUID propertyId, String unitNumber, BigDecimal baseRent, String waterMeterNumber) {
        Property property = propertyService.getById(propertyId);

        if (unitRepository.existsByPropertyIdAndUnitNumber(propertyId, unitNumber)) {
            throw new IllegalArgumentException(
                "Unit " + unitNumber + " already exists for this property");
        }

        Unit unit = Unit.builder()
            .property(property)
            .unitNumber(unitNumber)
            .baseRent(baseRent)
            .waterMeterNumber(waterMeterNumber)
            .status(UnitStatus.VACANT)
            .build();

        return unitRepository.save(unit);
    }

    public List<Unit> getByStatus(UnitStatus status) {
        return unitRepository.findByStatus(status);
    }

    public List<Unit> getByProperty(UUID propertyId) {
        return unitRepository.findByPropertyId(propertyId);
    }

    @Transactional
    public Unit updateStatus(UUID unitId, UnitStatus newStatus) {
        Unit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitId));

        unit.setStatus(newStatus);
        return unitRepository.save(unit);
    }
}