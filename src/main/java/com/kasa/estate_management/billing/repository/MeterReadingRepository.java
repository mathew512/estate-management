package com.kasa.estate_management.billing.repository;

import com.kasa.estate_management.billing.model.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {

    List<MeterReading> findByUnitIdOrderByReadingDateDesc(UUID unitId);

    Optional<MeterReading> findFirstByUnitIdOrderByReadingDateDesc(UUID unitId);

    @Query("select sum(m.totalCharge) from MeterReading m " +
           "where m.unit.id = :unitId and m.readingDate >= :fromDate and m.readingDate < :toDate")
    BigDecimal sumChargeForPeriod(@Param("unitId") UUID unitId,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate);
}