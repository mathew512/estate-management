package com.kasa.estate_management.maintenance.service;

import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.maintenance.model.Complaint;
import com.kasa.estate_management.maintenance.model.ComplaintCategory;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.LeaseStatus;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.repository.LeaseRepository;
import com.kasa.estate_management.tenant.service.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantComplaintService {

    private final ComplaintService complaintService;
    private final TenantService tenantService;
    private final LeaseRepository leaseRepository;

    public TenantComplaintService(ComplaintService complaintService,
                                  TenantService tenantService,
                                  LeaseRepository leaseRepository) {
        this.complaintService = complaintService;
        this.tenantService = tenantService;
        this.leaseRepository = leaseRepository;
    }

    @Transactional
    public Complaint create(User user, UUID unitId, ComplaintCategory category, String title, String description) {
        Tenant tenant = tenantService.getByUserId(user.getId());

        UUID resolvedUnitId = unitId;
        if (resolvedUnitId == null) {
            List<Lease> active = leaseRepository.findByTenantIdAndStatus(tenant.getId(), LeaseStatus.ACTIVE);
            if (active.isEmpty()) {
                throw new IllegalArgumentException("You have no active lease");
            }
            if (active.size() > 1) {
                throw new IllegalArgumentException("You have more than one active lease; say which unit this is for");
            }
            resolvedUnitId = active.get(0).getUnit().getId();
        }

        // Tenants never set the priority; staff triage it. The lease check in createComplaint
        // also guarantees a tenant can only raise complaints for a unit they currently rent.
        return complaintService.createComplaint(
            resolvedUnitId, tenant.getId(), category, title, description, null, user);
    }

    public List<Complaint> list(User user) {
        Tenant tenant = tenantService.getByUserId(user.getId());
        return complaintService.getByTenant(tenant.getId());
    }

    public Complaint get(User user, UUID complaintId) {
        Tenant tenant = tenantService.getByUserId(user.getId());
        Complaint complaint = complaintService.getById(complaintId);

        // Same message as a missing complaint, so a tenant can't probe for other tenants' ticket IDs.
        if (!complaint.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Complaint not found: " + complaintId);
        }
        return complaint;
    }
}