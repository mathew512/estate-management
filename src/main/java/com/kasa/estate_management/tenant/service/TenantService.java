package com.kasa.estate_management.tenant.service;

import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public Tenant onboardTenant(String fullName, String phoneNumber, String email, String nationalId) {
        if (tenantRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalArgumentException("A tenant with this phone number already exists");
        }

        Tenant tenant = Tenant.builder()
            .fullName(fullName)
            .phoneNumber(phoneNumber)
            .email(email)
            .nationalId(nationalId)
            .active(true)
            .build();

        return tenantRepository.save(tenant);
    }

    public Tenant getById(UUID id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));
    }
}