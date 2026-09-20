package com.kasa.estate_management.tenant.repository;

import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.LeaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaseRepository extends JpaRepository<Lease, UUID> {

    Optional<Lease> findByUnitIdAndStatus(UUID unitId, LeaseStatus status);

    List<Lease> findByTenantId(UUID tenantId);

    List<Lease> findByStatus(LeaseStatus status);
}