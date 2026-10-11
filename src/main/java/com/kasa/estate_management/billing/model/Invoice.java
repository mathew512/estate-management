package com.kasa.estate_management.billing.model;

import com.kasa.estate_management.tenant.model.Lease;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lease_id", nullable = false)
    private Lease lease;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "rent_amount", nullable = false)
    private BigDecimal rentAmount;

    @Column(name = "utility_amount", nullable = false)
    @Builder.Default
    private BigDecimal utilityAmount = BigDecimal.ZERO;

    @Column(name = "penalty_amount", nullable = false)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "total_amount", insertable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}