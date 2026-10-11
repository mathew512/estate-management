package com.kasa.estate_management.billing.repository;

import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByLeaseId(UUID leaseId);

    List<Invoice> findByStatus(InvoiceStatus status);

    Optional<Invoice> findByLeaseIdAndPeriodMonth(UUID leaseId, LocalDate periodMonth);

    boolean existsByLeaseIdAndPeriodMonth(UUID leaseId, LocalDate periodMonth);
}