package com.example.timetablegenerator.service;

import com.example.timetablegenerator.database.DBConnection;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class HistoryService {

    // Row for history list
    public static class RunRow {
        private UUID runId;
        private int version;
        private Timestamp generatedAt;
        private int rowsCount;
        private String runName;

        public UUID getRunId() { return runId; }
        public int getVersion() { return version; }
        public Timestamp getGeneratedAt() { return generatedAt; }
        public int getRowsCount() { return rowsCount; }
        public String getRunName() { return runName; }
    }

    // Row for a run’s timetable view
    public static class AllocationRow {
        public String divisionname;
        public String dayname;
        public int slotno;
        public String subjectname;
        public String facultyname;
        public String classname;
        public String session_type;
        public int semesternumber;
    }

    public List<RunRow> listRuns() {
        String sql = "SELECT run_id, version, generated_at, rows_count, run_name FROM v_timetable_runs ORDER BY generated_at DESC";
        List<RunRow> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RunRow r = new RunRow();
                r.runId = (UUID) rs.getObject("run_id");
                r.version = rs.getInt("version");
                r.generatedAt = rs.getTimestamp("generated_at");
                r.rowsCount = rs.getInt("rows_count");
                r.runName = rs.getString("run_name");
                out.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public List<AllocationRow> getRun(UUID runId) {
        String sql = "SELECT divisionname, dayname, slotno, subjectname, facultyname, classname, session_type, semesternumber " +
                     "FROM v_timetable_by_run WHERE run_id = ? ORDER BY divisionname, dayname, slotno";
        List<AllocationRow> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AllocationRow r = new AllocationRow();
                    r.divisionname = rs.getString("divisionname");
                    r.dayname = rs.getString("dayname");
                    r.slotno = rs.getInt("slotno");
                    r.subjectname = rs.getString("subjectname");
                    r.facultyname = rs.getString("facultyname");
                    r.classname = rs.getString("classname");
                    r.session_type = rs.getString("session_type");
                    r.semesternumber = rs.getInt("semesternumber");
                    out.add(r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public boolean deleteRun(UUID runId) {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement a = c.prepareStatement("DELETE FROM allocation WHERE run_id = ?")) {
                a.setObject(1, runId);
                a.executeUpdate();
            }
            try (PreparedStatement m = c.prepareStatement("DELETE FROM run_meta WHERE run_id = ?")) {
                m.setObject(1, runId);
                m.executeUpdate();
            }
            c.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean renameRun(UUID runId, String newName) {
        String sql = "INSERT INTO run_meta(run_id, run_name) VALUES(?, ?) " +
                     "ON CONFLICT (run_id) DO UPDATE SET run_name = EXCLUDED.run_name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, newName);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace(); return false;
        }
    }
}
