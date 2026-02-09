package com.example.timetablegenerator.controller;

import com.example.timetablegenerator.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    // current (in-memory) exports
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv() {
        String csv = exportService.exportCurrentAsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=timetable.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel() {
        byte[] excel = exportService.exportCurrentAsExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=timetable.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // history exports by runId
    @GetMapping("/history/csv")
    public ResponseEntity<byte[]> exportHistoryCsv(@RequestParam("runId") UUID runId) {
        String csv = exportService.exportRunAsCsv(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=timetable_" + runId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/history/excel")
    public ResponseEntity<byte[]> exportHistoryExcel(@RequestParam("runId") UUID runId) {
        byte[] excel = exportService.exportRunAsExcel(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=timetable_" + runId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
