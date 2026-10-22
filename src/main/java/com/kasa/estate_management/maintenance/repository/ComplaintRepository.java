package com.kasa.estate_management.maintenance.repository;

import com.kasa.estate_management.maintenance.model.Complaint;
import com.kasa.estate_management.maintenance.model.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    List<Complaint> findByUnitIdOrderByCreatedAtDesc(UUID unitId);

    List<Complaint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Complaint> findByAssignedToIdOrderByCreatedAtDesc(UUID userId);
}