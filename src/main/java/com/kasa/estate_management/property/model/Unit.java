package com.kasa.estate_management.property.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "unit_number", nullable = false)
    private String unitNumber;

    @Column(name = "base_rent", nullable = false)
    private BigDecimal baseRent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UnitStatus status = UnitStatus.VACANT;

    @Column(name = "water_meter_number")
    private String waterMeterNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}