package com.kasa.estate_management.property.repository;

import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.model.UnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {

    List<Unit> findByStatus(UnitStatus status);

    List<Unit> findByPropertyId(UUID propertyId);

    boolean existsByPropertyIdAndUnitNumber(UUID propertyId, String unitNumber);
}