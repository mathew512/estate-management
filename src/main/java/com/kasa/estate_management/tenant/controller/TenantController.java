package com.kasa.estate_management.tenant.controller;

import com.kasa.estate_management.tenant.dto.TenantDtos.OnboardTenantRequest;
import com.kasa.estate_management.tenant.dto.TenantDtos.TenantResponse;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> onboard(@Valid @RequestBody OnboardTenantRequest request) {
        Tenant tenant = tenantService.onboardTenant(
            request.fullName(), request.phoneNumber(), request.email(), request.nationalId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tenant));
    }

    @GetMapping("/{id}")
    public TenantResponse getById(@PathVariable UUID id) {
        return toResponse(tenantService.getById(id));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
            tenant.getId(),
            tenant.getFullName(),
            tenant.getPhoneNumber(),
            tenant.getEmail(),
            tenant.getNationalId(),
            tenant.isActive(),
            tenant.getCreatedAt()
        );
    }
}