package com.kasa.estate_management.billing.controller;

import com.kasa.estate_management.billing.dto.BillingDtos.MeterReadingResponse;
import com.kasa.estate_management.billing.dto.BillingDtos.RecordMeterReadingRequest;
import com.kasa.estate_management.billing.model.MeterReading;
import com.kasa.estate_management.billing.service.MeterReadingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meter-readings")
public class MeterReadingController {

    private final MeterReadingService meterReadingService;

    public MeterReadingController(MeterReadingService meterReadingService) {
        this.meterReadingService = meterReadingService;
    }

    @PostMapping
    public ResponseEntity<MeterReadingResponse> record(@Valid @RequestBody RecordMeterReadingRequest request) {
        MeterReading reading = meterReadingService.recordReading(
            request.unitId(), request.currentReading(), request.ratePerUnit(), request.readingDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reading));
    }

    private MeterReadingResponse toResponse(MeterReading r) {
        return new MeterReadingResponse(
            r.getId(), r.getUnit().getId(), r.getReadingDate(),
            r.getPreviousReading(), r.getCurrentReading(), r.getRatePerUnit(),
            r.getTotalCharge(), r.getCreatedAt()
        );
    }
}