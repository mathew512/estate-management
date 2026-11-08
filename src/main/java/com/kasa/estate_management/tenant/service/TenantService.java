package com.kasa.estate_management.tenant.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.auth.service.UserService;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final AuditService auditService;

    public TenantService(TenantRepository tenantRepository, UserService userService,
                         AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.userService = userService;
        this.auditService = auditService;
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

        Tenant saved = tenantRepository.save(tenant);
        // Personal details are deliberately left out of the audit trail; the entry id points at the tenant.
        auditService.record(AuditAction.TENANT_CREATED, "TENANT", saved.getId(), "Onboarded a new tenant");
        return saved;
    }

    /** Creates a TENANT-role login for an existing tenant. The role is fixed; it cannot be chosen. */
    @Transactional
    public Tenant createLoginAccount(UUID tenantId, String email, String rawPassword) {
        Tenant tenant = getById(tenantId);

        if (tenant.getUser() != null) {
            throw new IllegalArgumentException("This tenant already has a login account");
        }

        User user = userService.registerUser(
            tenant.getFullName(), email, tenant.getPhoneNumber(), rawPassword, Role.TENANT);

        tenant.setUser(user);
        Tenant saved = tenantRepository.save(tenant);

        auditService.record(AuditAction.TENANT_LOGIN_CREATED, "TENANT", saved.getId(),
            "Created a login account (user " + user.getId() + ") for the tenant");
        return saved;
    }

    public Tenant getById(UUID id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));
    }

    public Tenant getByUserId(UUID userId) {
        return tenantRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("No tenant profile is linked to this account"));
    }
}