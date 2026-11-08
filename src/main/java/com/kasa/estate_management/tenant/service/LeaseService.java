package com.kasa.estate_management.tenant.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.model.UnitStatus;
import com.kasa.estate_management.property.repository.UnitRepository;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.LeaseStatus;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.repository.LeaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final TenantService tenantService;
    private final AuditService auditService;

    public LeaseService(LeaseRepository leaseRepository, UnitRepository unitRepository,
                        TenantService tenantService, AuditService auditService) {
        this.leaseRepository = leaseRepository;
        this.unitRepository = unitRepository;
        this.tenantService = tenantService;
        this.auditService = auditService;
    }

    @Transactional
    public Lease createLease(UUID unitId, UUID tenantId, LocalDate startDate, BigDecimal monthlyRent, BigDecimal depositAmount) {
        Unit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitId));

        Tenant tenant = tenantService.getById(tenantId);

        if (leaseRepository.findByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE).isPresent()) {
            throw new IllegalArgumentException("This unit already has an active lease");
        }

        Lease lease = Lease.builder()
            .unit(unit)
            .tenant(tenant)
            .startDate(startDate)
            .monthlyRent(monthlyRent)
            .depositAmount(depositAmount)
            .status(LeaseStatus.ACTIVE)
            .build();

        Lease savedLease = leaseRepository.save(lease);

        unit.setStatus(UnitStatus.OCCUPIED);
        unitRepository.save(unit);

        auditService.record(AuditAction.LEASE_CREATED, "LEASE", savedLease.getId(),
            "Created lease for unit " + unit.getUnitNumber() + " (tenant " + tenantId + "): rent "
                + monthlyRent.toPlainString() + ", deposit " + depositAmount.toPlainString()
                + ", starting " + startDate + "; unit set to OCCUPIED");

        return savedLease;
    }

    @Transactional
    public Lease endLease(UUID leaseId) {
        Lease lease = leaseRepository.findById(leaseId)
            .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active lease can be ended");
        }

        lease.setStatus(LeaseStatus.ENDED);
        lease.setEndDate(LocalDate.now());
        Lease savedLease = leaseRepository.save(lease);

        Unit unit = lease.getUnit();
        unit.setStatus(UnitStatus.VACANT);
        unitRepository.save(unit);

        auditService.record(AuditAction.LEASE_ENDED, "LEASE", savedLease.getId(),
            "Ended lease for unit " + unit.getUnitNumber() + " on " + savedLease.getEndDate()
                + "; unit set to VACANT");

        return savedLease;
    }

    public Lease getById(UUID id) {
        return leaseRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + id));
    }
}