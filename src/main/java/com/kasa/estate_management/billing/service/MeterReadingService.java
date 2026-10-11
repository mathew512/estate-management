package com.kasa.estate_management.billing.service;

import com.kasa.estate_management.billing.model.MeterReading;
import com.kasa.estate_management.billing.repository.MeterReadingRepository;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class MeterReadingService {

    private final MeterReadingRepository meterReadingRepository;
    private final UnitRepository unitRepository;

    public MeterReadingService(MeterReadingRepository meterReadingRepository, UnitRepository unitRepository) {
        this.meterReadingRepository = meterReadingRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional
    public MeterReading recordReading(UUID unitId, BigDecimal currentReading, BigDecimal ratePerUnit, LocalDate readingDate) {
        Unit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitId));

        BigDecimal previousReading = meterReadingRepository
            .findFirstByUnitIdOrderByReadingDateDesc(unitId)
            .map(MeterReading::getCurrentReading)
            .orElse(BigDecimal.ZERO);

        if (currentReading.compareTo(previousReading) < 0) {
            throw new IllegalArgumentException(
                "Current reading cannot be less than the previous reading (" + previousReading + ")");
        }

        MeterReading reading = MeterReading.builder()
            .unit(unit)
            .readingDate(readingDate)
            .previousReading(previousReading)
            .currentReading(currentReading)
            .ratePerUnit(ratePerUnit)
            .build();

        return meterReadingRepository.save(reading);
    }
}