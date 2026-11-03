package com.kasa.estate_management.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReportDtos {

    public record PropertyOccupancy(
        UUID propertyId,
        String propertyName,
        long totalUnits,
        long occupied,
        long vacant,
        long maintenance,
        BigDecimal occupancyRate
    ) {}

    public record OccupancyReport(
        long totalUnits,
        long occupied,
        long vacant,
        long maintenance,
        BigDecimal occupancyRate,
        List<PropertyOccupancy> properties
    ) {}

    public record PropertyCollections(
        UUID propertyId,
        String propertyName,
        long invoiceCount,
        BigDecimal invoiced,
        BigDecimal collected,
        BigDecimal outstanding,
        BigDecimal collectionRate
    ) {}

    public record CollectionsReport(
        LocalDate month,
        long invoiceCount,
        BigDecimal invoiced,
        BigDecimal collected,
        BigDecimal outstanding,
        BigDecimal collectionRate,
        List<PropertyCollections> properties
    ) {}

    public record ArrearsItem(
        UUID invoiceId,
        String propertyName,
        String unitNumber,
        String tenantName,
        String tenantPhone,
        LocalDate periodMonth,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balance,
        int daysOverdue
    ) {}

    public record AgingBucket(String label, long invoices, BigDecimal balance) {}

    public record ArrearsReport(
        LocalDate asOf,
        long invoiceCount,
        BigDecimal totalOutstanding,
        List<AgingBucket> aging,
        List<ArrearsItem> items
    ) {}

    public record DashboardSummary(
        LocalDate asOf,
        long totalUnits,
        long occupiedUnits,
        BigDecimal occupancyRate,
        LocalDate month,
        BigDecimal invoiced,
        BigDecimal collected,
        BigDecimal collectionRate,
        long overdueInvoices,
        BigDecimal totalArrears,
        long openComplaints
    ) {}
}