package com.kasa.estate_management.property.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
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
    private final AuditService auditService;

    public UnitService(UnitRepository unitRepository, PropertyService propertyService,
                       AuditService auditService) {
        this.unitRepository = unitRepository;
        this.propertyService = propertyService;
        this.auditService = auditService;
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

        Unit saved = unitRepository.save(unit);
        auditService.record(AuditAction.UNIT_CREATED, "UNIT", saved.getId(),
            "Added unit " + unitNumber + " to property \"" + property.getName()
                + "\" with base rent " + baseRent.toPlainString());
        return saved;
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

        UnitStatus previous = unit.getStatus();
        unit.setStatus(newStatus);
        Unit saved = unitRepository.save(unit);

        if (previous != newStatus) {
            auditService.record(AuditAction.UNIT_STATUS_CHANGED, "UNIT", saved.getId(),
                "Unit " + saved.getUnitNumber() + " status changed from " + previous + " to " + newStatus);
        }
        return saved;
    }
}