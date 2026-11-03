package com.kasa.estate_management.report.controller;

import com.kasa.estate_management.report.dto.ReportDtos.ArrearsReport;
import com.kasa.estate_management.report.dto.ReportDtos.CollectionsReport;
import com.kasa.estate_management.report.dto.ReportDtos.DashboardSummary;
import com.kasa.estate_management.report.dto.ReportDtos.OccupancyReport;
import com.kasa.estate_management.report.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/occupancy")
    public OccupancyReport occupancy() {
        return reportService.occupancy();
    }

    @GetMapping("/collections")
    public CollectionsReport collections(
        @RequestParam(name = "month", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month
    ) {
        LocalDate target = (month != null) ? month : ReportService.today();
        return reportService.collections(target);
    }

    @GetMapping("/arrears")
    public ArrearsReport arrears(
        @RequestParam(name = "asOf", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return reportService.arrears(asOf != null ? asOf : ReportService.today());
    }

    @GetMapping("/summary")
    public DashboardSummary summary(
        @RequestParam(name = "asOf", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return reportService.summary(asOf != null ? asOf : ReportService.today());
    }
}