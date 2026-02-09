package com.example.timetablegenerator.service;

import com.example.timetablegenerator.database.DBConnection;
import com.example.timetablegenerator.service.TimetableService.Lecture;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.*;

@Service
public class ExportService {

    private final TimetableService timetableService;

    public ExportService(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    // -------- current (in-memory) exports --------
    public String exportCurrentAsCsv() {
        Map<String, Map<String, Map<Integer, Lecture>>> table = timetableService.getTimetable();
        if (table == null || table.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("Division,Day,Slot,Type,Faculty,Subject\n");
        for (String division : table.keySet()) {
            Map<String, Map<Integer, Lecture>> byDay = table.get(division);
            for (String day : byDay.keySet()) {
                Map<Integer, Lecture> slots = byDay.get(day);
                for (int slot : new TreeSet<>(slots.keySet())) {
                    Lecture lec = slots.get(slot);
                    String type = lec.getSessionType();
                    String faculty = lec.getFaculty() == null ? "" : lec.getFaculty();
                    String subject = lec.getSubject() == null ? "" : lec.getSubject();
                    sb.append(escapeCsv(division)).append(",")
                      .append(escapeCsv(day)).append(",")
                      .append(slot).append(",")
                      .append(escapeCsv(type)).append(",")
                      .append(escapeCsv(faculty)).append(",")
                      .append(escapeCsv(subject)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    public byte[] exportCurrentAsExcel() {
        Map<String, Map<String, Map<Integer, Lecture>>> table = timetableService.getTimetable();
        if (table == null || table.isEmpty()) return new byte[0];

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Timetable");
            Row header = sh.createRow(0);
            String[] cols = {"Division","Day","Slot","Type","Faculty","Subject"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int r = 1;
            for (String division : table.keySet()) {
                Map<String, Map<Integer, Lecture>> byDay = table.get(division);
                for (String day : byDay.keySet()) {
                    Map<Integer, Lecture> slots = byDay.get(day);
                    for (int slot : new TreeSet<>(slots.keySet())) {
                        Lecture lec = slots.get(slot);
                        Row row = sh.createRow(r++);
                        row.createCell(0).setCellValue(division);
                        row.createCell(1).setCellValue(day);
                        row.createCell(2).setCellValue(slot);
                        row.createCell(3).setCellValue(lec.getSessionType());
                        row.createCell(4).setCellValue(lec.getFaculty() == null ? "" : lec.getFaculty());
                        row.createCell(5).setCellValue(lec.getSubject() == null ? "" : lec.getSubject());
                    }
                }
            }
            for (int i = 0; i < cols.length; i++) sh.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    // -------- history (DB) exports by runId --------
    public String exportRunAsCsv(java.util.UUID runId) {
        String sql = "SELECT divisionname, dayname, slotno, session_type, facultyname, subjectname " +
                     "FROM v_timetable_by_run WHERE run_id = ? ORDER BY divisionname, dayname, slotno";
        StringBuilder sb = new StringBuilder("Division,Day,Slot,Type,Faculty,Subject\n");
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(escapeCsv(rs.getString("divisionname"))).append(",")
                      .append(escapeCsv(rs.getString("dayname"))).append(",")
                      .append(rs.getInt("slotno")).append(",")
                      .append(escapeCsv(rs.getString("session_type"))).append(",")
                      .append(escapeCsv(rs.getString("facultyname"))).append(",")
                      .append(escapeCsv(rs.getString("subjectname"))).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public byte[] exportRunAsExcel(java.util.UUID runId) {
        String sql = "SELECT divisionname, dayname, slotno, session_type, facultyname, subjectname " +
                     "FROM v_timetable_by_run WHERE run_id = ? ORDER BY divisionname, dayname, slotno";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery();
                 Workbook wb = new XSSFWorkbook()) {
                Sheet sh = wb.createSheet("Timetable");
                Row header = sh.createRow(0);
                String[] cols = {"Division","Day","Slot","Type","Faculty","Subject"};
                for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

                int r = 1;
                while (rs.next()) {
                    Row row = sh.createRow(r++);
                    row.createCell(0).setCellValue(rs.getString("divisionname"));
                    row.createCell(1).setCellValue(rs.getString("dayname"));
                    row.createCell(2).setCellValue(rs.getInt("slotno"));
                    row.createCell(3).setCellValue(rs.getString("session_type"));
                    row.createCell(4).setCellValue(rs.getString("facultyname"));
                    row.createCell(5).setCellValue(rs.getString("subjectname"));
                }
                for (int i = 0; i < 6; i++) sh.autoSizeColumn(i);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        String v = s.replace("\"","\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }
}
