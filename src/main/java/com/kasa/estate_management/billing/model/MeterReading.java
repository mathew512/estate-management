package com.kasa.estate_management.billing.model;

import com.kasa.estate_management.property.model.Unit;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "meter_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "previous_reading", nullable = false)
    private BigDecimal previousReading;

    @Column(name = "current_reading", nullable = false)
    private BigDecimal currentReading;

    @Column(name = "rate_per_unit", nullable = false)
    private BigDecimal ratePerUnit;

    @Generated(event = EventType.INSERT)
    @Column(name = "total_charge", insertable = false, updatable = false)
    private BigDecimal totalCharge;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}