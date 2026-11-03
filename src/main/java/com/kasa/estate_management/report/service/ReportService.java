package com.kasa.estate_management.report.service;

import com.kasa.estate_management.report.dto.ReportDtos.AgingBucket;
import com.kasa.estate_management.report.dto.ReportDtos.ArrearsItem;
import com.kasa.estate_management.report.dto.ReportDtos.ArrearsReport;
import com.kasa.estate_management.report.dto.ReportDtos.CollectionsReport;
import com.kasa.estate_management.report.dto.ReportDtos.DashboardSummary;
import com.kasa.estate_management.report.dto.ReportDtos.OccupancyReport;
import com.kasa.estate_management.report.dto.ReportDtos.PropertyCollections;
import com.kasa.estate_management.report.dto.ReportDtos.PropertyOccupancy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcClient jdbc;

    public ReportService(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public static LocalDate today() {
        return LocalDate.now(NAIROBI);
    }

    @Transactional(readOnly = true)
    public OccupancyReport occupancy() {
        List<PropertyOccupancy> rows = jdbc.sql("""
            SELECT p.id AS property_id, p.name AS property_name,
                   COUNT(u.id) AS total_units,
                   COUNT(*) FILTER (WHERE u.status = 'OCCUPIED') AS occupied,
                   COUNT(*) FILTER (WHERE u.status = 'VACANT') AS vacant,
                   COUNT(*) FILTER (WHERE u.status = 'MAINTENANCE') AS maintenance
            FROM properties p
            LEFT JOIN units u ON u.property_id = p.id
            GROUP BY p.id, p.name
            ORDER BY p.name
            """)
            .query((rs, rowNum) -> {
                long total = rs.getLong("total_units");
                long occupied = rs.getLong("occupied");
                return new PropertyOccupancy(
                    rs.getObject("property_id", UUID.class),
                    rs.getString("property_name"),
                    total,
                    occupied,
                    rs.getLong("vacant"),
                    rs.getLong("maintenance"),
                    percent(occupied, total));
            })
            .list();

        long total = rows.stream().mapToLong(PropertyOccupancy::totalUnits).sum();
        long occupied = rows.stream().mapToLong(PropertyOccupancy::occupied).sum();
        long vacant = rows.stream().mapToLong(PropertyOccupancy::vacant).sum();
        long maintenance = rows.stream().mapToLong(PropertyOccupancy::maintenance).sum();

        return new OccupancyReport(total, occupied, vacant, maintenance, percent(occupied, total), rows);
    }

    /** Invoiced against collected for the invoices of one billing month. */
    @Transactional(readOnly = true)
    public CollectionsReport collections(LocalDate month) {
        LocalDate period = month.withDayOfMonth(1);

        List<PropertyCollections> rows = jdbc.sql("""
            SELECT p.id AS property_id, p.name AS property_name,
                   COUNT(i.id) AS invoice_count,
                   COALESCE(SUM(i.total_amount), 0) AS invoiced,
                   COALESCE(SUM(i.amount_paid), 0) AS collected
            FROM invoices i
            JOIN leases l ON l.id = i.lease_id
            JOIN units u ON u.id = l.unit_id
            JOIN properties p ON p.id = u.property_id
            WHERE i.period_month = :month
            GROUP BY p.id, p.name
            ORDER BY p.name
            """)
            .param("month", Date.valueOf(period))
            .query((rs, rowNum) -> {
                BigDecimal invoiced = rs.getBigDecimal("invoiced");
                BigDecimal collected = rs.getBigDecimal("collected");
                return new PropertyCollections(
                    rs.getObject("property_id", UUID.class),
                    rs.getString("property_name"),
                    rs.getLong("invoice_count"),
                    invoiced,
                    collected,
                    invoiced.subtract(collected),
                    percent(collected, invoiced));
            })
            .list();

        long count = rows.stream().mapToLong(PropertyCollections::invoiceCount).sum();
        BigDecimal invoiced = rows.stream().map(PropertyCollections::invoiced)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = rows.stream().map(PropertyCollections::collected)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CollectionsReport(period, count, invoiced, collected,
            invoiced.subtract(collected), percent(collected, invoiced), rows);
    }

    /** Every unpaid invoice whose due date is before {@code asOf}, oldest first. */
    @Transactional(readOnly = true)
    public ArrearsReport arrears(LocalDate asOf) {
        List<ArrearsItem> items = jdbc.sql("""
            SELECT i.id AS invoice_id, p.name AS property_name, u.unit_number,
                   t.full_name AS tenant_name, t.phone_number AS tenant_phone,
                   i.period_month, i.due_date, i.total_amount, i.amount_paid,
                   (i.total_amount - i.amount_paid) AS balance,
                   (CAST(:asOf AS date) - i.due_date) AS days_overdue
            FROM invoices i
            JOIN leases l ON l.id = i.lease_id
            JOIN units u ON u.id = l.unit_id
            JOIN properties p ON p.id = u.property_id
            JOIN tenants t ON t.id = l.tenant_id
            WHERE i.status IN ('PENDING', 'PARTIALLY_PAID', 'OVERDUE')
              AND i.due_date < CAST(:asOf AS date)
              AND i.total_amount > i.amount_paid
            ORDER BY days_overdue DESC, balance DESC
            """)
            .param("asOf", Date.valueOf(asOf))
            .query((rs, rowNum) -> new ArrearsItem(
                rs.getObject("invoice_id", UUID.class),
                rs.getString("property_name"),
                rs.getString("unit_number"),
                rs.getString("tenant_name"),
                rs.getString("tenant_phone"),
                rs.getObject("period_month", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("amount_paid"),
                rs.getBigDecimal("balance"),
                rs.getInt("days_overdue")))
            .list();

        String[] labels = {"1-30 days", "31-60 days", "61-90 days", "Over 90 days"};
        long[] counts = new long[4];
        BigDecimal[] sums = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal total = BigDecimal.ZERO;

        for (ArrearsItem item : items) {
            int bucket = bucketFor(item.daysOverdue());
            counts[bucket]++;
            sums[bucket] = sums[bucket].add(item.balance());
            total = total.add(item.balance());
        }

        List<AgingBucket> aging = List.of(
            new AgingBucket(labels[0], counts[0], sums[0]),
            new AgingBucket(labels[1], counts[1], sums[1]),
            new AgingBucket(labels[2], counts[2], sums[2]),
            new AgingBucket(labels[3], counts[3], sums[3]));

        return new ArrearsReport(asOf, items.size(), total, aging, items);
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(LocalDate asOf) {
        OccupancyReport occupancy = occupancy();
        CollectionsReport collections = collections(asOf.withDayOfMonth(1));
        ArrearsReport arrears = arrears(asOf);

        long openComplaints = jdbc.sql(
                "SELECT COUNT(*) FROM complaints WHERE status IN ('PENDING', 'IN_PROGRESS')")
            .query(Long.class)
            .single();

        return new DashboardSummary(
            asOf,
            occupancy.totalUnits(),
            occupancy.occupied(),
            occupancy.occupancyRate(),
            collections.month(),
            collections.invoiced(),
            collections.collected(),
            collections.collectionRate(),
            arrears.invoiceCount(),
            arrears.totalOutstanding(),
            openComplaints);
    }

    private static int bucketFor(int daysOverdue) {
        if (daysOverdue <= 30) {
            return 0;
        }
        if (daysOverdue <= 60) {
            return 1;
        }
        if (daysOverdue <= 90) {
            return 2;
        }
        return 3;
    }

    private static BigDecimal percent(long part, long whole) {
        return percent(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }

    /** A percentage to one decimal place, or null when there is nothing to divide by. */
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}