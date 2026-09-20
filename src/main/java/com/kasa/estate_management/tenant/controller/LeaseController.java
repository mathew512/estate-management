package com.kasa.estate_management.tenant.controller;

import com.kasa.estate_management.tenant.dto.LeaseDtos.CreateLeaseRequest;
import com.kasa.estate_management.tenant.dto.LeaseDtos.LeaseResponse;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.service.LeaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leases")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    public ResponseEntity<LeaseResponse> create(@Valid @RequestBody CreateLeaseRequest request) {
        BigDecimal deposit = request.depositAmount() != null ? request.depositAmount() : BigDecimal.ZERO;

        Lease lease = leaseService.createLease(
            request.unitId(), request.tenantId(), request.startDate(), request.monthlyRent(), deposit);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(lease));
    }

    @PatchMapping("/{id}/end")
    public LeaseResponse endLease(@PathVariable UUID id) {
        return toResponse(leaseService.endLease(id));
    }

    @GetMapping("/{id}")
    public LeaseResponse getById(@PathVariable UUID id) {
        return toResponse(leaseService.getById(id));
    }

    private LeaseResponse toResponse(Lease lease) {
        return new LeaseResponse(
            lease.getId(),
            lease.getUnit().getId(),
            lease.getUnit().getUnitNumber(),
            lease.getTenant().getId(),
            lease.getTenant().getFullName(),
            lease.getStartDate(),
            lease.getEndDate(),
            lease.getMonthlyRent(),
            lease.getDepositAmount(),
            lease.getStatus(),
            lease.getCreatedAt()
        );
    }
}