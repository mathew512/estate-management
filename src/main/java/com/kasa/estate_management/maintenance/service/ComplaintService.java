package com.kasa.estate_management.maintenance.service;

import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.auth.repository.UserRepository;
import com.kasa.estate_management.maintenance.model.Complaint;
import com.kasa.estate_management.maintenance.model.ComplaintCategory;
import com.kasa.estate_management.maintenance.model.ComplaintPriority;
import com.kasa.estate_management.maintenance.model.ComplaintStatus;
import com.kasa.estate_management.maintenance.repository.ComplaintRepository;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.repository.UnitRepository;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.LeaseStatus;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.repository.LeaseRepository;
import com.kasa.estate_management.tenant.service.TenantService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ComplaintService {

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.of(
        ComplaintStatus.PENDING, Set.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.CLOSED),
        ComplaintStatus.IN_PROGRESS, Set.of(ComplaintStatus.RESOLVED),
        ComplaintStatus.RESOLVED, Set.of(ComplaintStatus.CLOSED, ComplaintStatus.IN_PROGRESS),
        ComplaintStatus.CLOSED, Set.of()
    );

    private static final Set<Role> ASSIGNABLE_ROLES =
        Set.of(Role.ADMIN, Role.PROPERTY_MANAGER, Role.CARETAKER);

    private final ComplaintRepository complaintRepository;
    private final UnitRepository unitRepository;
    private final LeaseRepository leaseRepository;
    private final TenantService tenantService;
    private final UserRepository userRepository;

    public ComplaintService(ComplaintRepository complaintRepository,
                            UnitRepository unitRepository,
                            LeaseRepository leaseRepository,
                            TenantService tenantService,
                            UserRepository userRepository) {
        this.complaintRepository = complaintRepository;
        this.unitRepository = unitRepository;
        this.leaseRepository = leaseRepository;
        this.tenantService = tenantService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Complaint createComplaint(UUID unitId, UUID tenantId, ComplaintCategory category,
                                     String title, String description,
                                     ComplaintPriority priority, User createdBy) {
        Unit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitId));
        Tenant tenant = tenantService.getById(tenantId);

        Lease lease = leaseRepository.findByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("This unit has no active lease"));

        if (!lease.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("This tenant does not currently lease this unit");
        }

        Complaint complaint = Complaint.builder()
            .unit(unit)
            .tenant(tenant)
            .category(category)
            .title(title.trim())
            .description(description.trim())
            .priority(priority != null ? priority : ComplaintPriority.NORMAL)
            .status(ComplaintStatus.PENDING)
            .createdBy(createdBy)
            .build();

        return complaintRepository.save(complaint);
    }

    /**
     * Assigns the complaint to a staff member. A PENDING complaint moves to IN_PROGRESS
     * as soon as someone owns it.
     */
    @Transactional
    public Complaint assign(UUID complaintId, UUID staffUserId) {
        Complaint complaint = getById(complaintId);

        if (complaint.getStatus() == ComplaintStatus.RESOLVED
            || complaint.getStatus() == ComplaintStatus.CLOSED) {
            throw new IllegalArgumentException("A resolved or closed complaint cannot be assigned");
        }

        User staff = userRepository.findById(staffUserId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + staffUserId));

        if (!staff.isActive()) {
            throw new IllegalArgumentException("This user account is disabled");
        }
        if (!ASSIGNABLE_ROLES.contains(staff.getRole())) {
            throw new IllegalArgumentException("Complaints can only be assigned to admins, managers or caretakers");
        }

        complaint.setAssignedTo(staff);
        if (complaint.getStatus() == ComplaintStatus.PENDING) {
            complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        }

        return complaintRepository.save(complaint);
    }

    @Transactional
    public Complaint updateStatus(UUID complaintId, ComplaintStatus newStatus, String notes, User actingUser) {
        Complaint complaint = getById(complaintId);
        ComplaintStatus current = complaint.getStatus();
        boolean hasNotes = notes != null && !notes.isBlank();

        if (actingUser.getRole() == Role.CARETAKER) {
            boolean isAssignee = complaint.getAssignedTo() != null
                && complaint.getAssignedTo().getId().equals(actingUser.getId());
            if (!isAssignee) {
                throw new AccessDeniedException("Caretakers can only update complaints assigned to them");
            }
            if (newStatus == ComplaintStatus.CLOSED) {
                throw new AccessDeniedException("Only a manager or admin can close a complaint");
            }
        }

        if (current == newStatus) {
            throw new IllegalArgumentException("The complaint is already " + current);
        }
        if (!ALLOWED_TRANSITIONS.get(current).contains(newStatus)) {
            throw new IllegalArgumentException(
                "A complaint cannot move from " + current + " to " + newStatus);
        }
        if (current == ComplaintStatus.PENDING && newStatus == ComplaintStatus.IN_PROGRESS
            && complaint.getAssignedTo() == null) {
            throw new IllegalArgumentException("Assign the complaint to a staff member before starting work");
        }
        if (current == ComplaintStatus.PENDING && newStatus == ComplaintStatus.CLOSED && !hasNotes) {
            throw new IllegalArgumentException("Give a reason when closing a complaint that was never worked on");
        }

        if (newStatus == ComplaintStatus.RESOLVED) {
            if (!hasNotes) {
                throw new IllegalArgumentException("Resolution notes are required to resolve a complaint");
            }
            complaint.setResolutionNotes(notes.trim());
            complaint.setResolvedAt(Instant.now());
        } else if (current == ComplaintStatus.RESOLVED && newStatus == ComplaintStatus.IN_PROGRESS) {
            complaint.setResolvedAt(null); // reopened
        } else if (newStatus == ComplaintStatus.CLOSED && hasNotes) {
            complaint.setResolutionNotes(notes.trim());
        }

        complaint.setStatus(newStatus);
        return complaintRepository.save(complaint);
    }

    public Complaint getById(UUID id) {
        return complaintRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + id));
    }

    public List<Complaint> getAll() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Complaint> getByStatus(ComplaintStatus status) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<Complaint> getByUnit(UUID unitId) {
        return complaintRepository.findByUnitIdOrderByCreatedAtDesc(unitId);
    }

    public List<Complaint> getByTenant(UUID tenantId) {
        return complaintRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public List<Complaint> getByAssignee(UUID userId) {
        return complaintRepository.findByAssignedToIdOrderByCreatedAtDesc(userId);
    }
}