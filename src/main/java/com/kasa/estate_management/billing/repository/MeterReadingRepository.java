package com.kasa.estate_management.billing.repository;

import com.kasa.estate_management.billing.model.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {

    List<MeterReading> findByUnitIdOrderByReadingDateDesc(UUID unitId);

    Optional<MeterReading> findFirstByUnitIdOrderByReadingDateDesc(UUID unitId);
}