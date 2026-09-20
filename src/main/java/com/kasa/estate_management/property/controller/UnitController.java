package com.kasa.estate_management.property.controller;

import com.kasa.estate_management.property.dto.UnitDtos.CreateUnitRequest;
import com.kasa.estate_management.property.dto.UnitDtos.UnitResponse;
import com.kasa.estate_management.property.dto.UnitDtos.UpdateUnitStatusRequest;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.model.UnitStatus;
import com.kasa.estate_management.property.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @PostMapping("/properties/{propertyId}/units")
    public ResponseEntity<UnitResponse> addUnit(
        @PathVariable UUID propertyId,
        @Valid @RequestBody CreateUnitRequest request
    ) {
        Unit unit = unitService.addUnit(
            propertyId, request.unitNumber(), request.baseRent(), request.waterMeterNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(unit));
    }

    @GetMapping("/units")
    public List<UnitResponse> getByStatus(@RequestParam(required = false) UnitStatus status) {
        List<Unit> units = (status != null)
            ? unitService.getByStatus(status)
            : unitService.getByStatus(UnitStatus.VACANT); // default view
        return units.stream().map(this::toResponse).toList();
    }

    @GetMapping("/properties/{propertyId}/units")
    public List<UnitResponse> getByProperty(@PathVariable UUID propertyId) {
        return unitService.getByProperty(propertyId).stream().map(this::toResponse).toList();
    }

    @PatchMapping("/units/{unitId}/status")
    public UnitResponse updateStatus(
        @PathVariable UUID unitId,
        @Valid @RequestBody UpdateUnitStatusRequest request
    ) {
        Unit unit = unitService.updateStatus(unitId, request.status());
        return toResponse(unit);
    }

    private UnitResponse toResponse(Unit unit) {
        return new UnitResponse(
            unit.getId(),
            unit.getProperty().getId(),
            unit.getProperty().getName(),
            unit.getUnitNumber(),
            unit.getBaseRent(),
            unit.getStatus(),
            unit.getWaterMeterNumber()
        );
    }
}