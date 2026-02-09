package com.example.timetablegenerator.controller;

import com.example.timetablegenerator.service.TimetableService;
import com.example.timetablegenerator.service.TimetableService.Lecture;
import com.example.timetablegenerator.service.TimetableService.SubjectPlan;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

import com.example.timetablegenerator.database.DBConnection;

@Controller
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping("/")
    public String showForm(Model model) {
        return "index";
    }

    // Prevent accidental GET to /generate from being treated as a static resource
    @GetMapping("/generate")
    public String redirectGenerateGet() {
        return "redirect:/";
    }

    @PostMapping(value = "/generate")
    public String generateTimetable(
            @RequestParam("numDays") int numDays,
            @RequestParam("days") String daysStr,
            @RequestParam("numSlots") int numSlots,
            @RequestParam("numDiv") int numDiv,
            @RequestParam("divisions") String divisionsStr,
            @RequestParam("subjectNames") String subjectNamesStr,
            @RequestParam(value = "labSubjects", required = false) String labSubjectsStr,
            @RequestParam(value = "recessRanges", required = false) String recessRangesStr,
            @RequestParam("maxLecturesPerDay") int maxLecturesPerDay,
            @RequestParam("totalLectures") int totalLectures,
            @RequestParam("totalLabs") int totalLabs,
            @RequestParam(value = "planCsv", required = false) String planCsv,
            @RequestParam(value = "planData", required = false) String planJson, // optional JSON from UI table
            @RequestParam(value = "runName", required = false) String runName,   // NEW: optional friendly name
            Model model
    ) {
        try {
            List<String> days = splitCsv(daysStr);
            List<String> divisions = splitCsv(divisionsStr);
            List<String> subjectNames = splitCsv(subjectNamesStr);
            List<String> labSubjects = splitCsvNullable(labSubjectsStr);
            List<int[]> recesses = parseRecessRanges(recessRangesStr);

            Map<String, Map<String, SubjectPlan>> plan = new HashMap<>();
            if (planCsv != null && !planCsv.trim().isEmpty()) {
                plan = parsePlanCsv(planCsv);
            } else if (planJson != null && !planJson.trim().isEmpty()) {
                plan = parsePlanJson(planJson);
            }

            if (!plan.isEmpty()) {
                timetableService.setInputsWithPlan(days, numSlots, divisions, recesses, maxLecturesPerDay, plan);
            } else {
                timetableService.setInputs(
                        days, numSlots, divisions, subjectNames, labSubjects, recesses,
                        maxLecturesPerDay, totalLectures, totalLabs
                );
            }

            boolean success = timetableService.generateTimetable();
            if (!success) return withError(model, "Failed to generate timetable.");

            Map<String, Map<String, Map<Integer, Lecture>>> table = timetableService.getTimetable();
            if (table == null || table.isEmpty()) return withError(model, "No timetable generated.");

            // defaults for view
            if (days == null || days.isEmpty()) days = Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri");
            if (divisions == null || divisions.isEmpty()) divisions = new ArrayList<>(table.keySet());
            if (numSlots <= 0) numSlots = 8;

            // --------- Versioning: runId + version + runName ----------
            UUID runId = UUID.randomUUID();
            int version = fetchNextVersion();
            String finalRunName = (runName != null && !runName.isBlank())
                    ? runName.trim()
                    : "Run " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            boolean saved = false;
            try {
                saved = timetableService.saveToDatabase(runId, version, finalRunName);
            } catch (Throwable t) {
                System.out.println("⚠️ Skipping DB save due to unexpected error: " + t.getMessage());
            }

            model.addAttribute("dbStatus", saved
                    ? "Timetable saved successfully to database."
                    : "Timetable generated, but saving failed.");

            model.addAttribute("days", days);
            model.addAttribute("divisions", divisions);
            model.addAttribute("numSlots", numSlots);
            model.addAttribute("timetable", table);

            // for UI notice + history button
            model.addAttribute("version", version);
            model.addAttribute("runId", runId.toString());
            model.addAttribute("runName", finalRunName);

            // Pre-rendered HTML tables to avoid Thymeleaf iteration pitfalls
            Map<String, String> htmlTables = buildDivisionTablesHtml(days, numSlots, table);
            model.addAttribute("htmlTables", htmlTables);

            System.out.println("✅ Timetable generated successfully for "
                    + divisions.size() + " divisions × " + days.size() + " days × "
                    + numSlots + " slots.");

            return "timetable";

        } catch (Exception e) {
            e.printStackTrace();
            return withError(model, "Error: " + e.getMessage());
        }
    }

    private int fetchNextVersion() {
        String sql = "SELECT COALESCE(MAX(version),0) + 1 AS next_ver FROM allocation";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("next_ver");
        } catch (Exception e) {
            System.out.println("⚠️ Could not fetch next version, defaulting to 1: " + e.getMessage());
        }
        return 1;
    }

    private String withError(Model model, String msg) {
        model.addAttribute("error", msg);
        return "index";
    }

    // ---------- Helpers ----------
    private List<String> splitCsv(String s) {
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> splitCsvNullable(String s) {
        if (s == null || s.trim().isEmpty()) return Collections.emptyList();
        return splitCsv(s);
    }

    private List<int[]> parseRecessRanges(String ranges) {
        List<int[]> res = new ArrayList<>();
        if (ranges == null || ranges.trim().isEmpty()) return res;
        for (String t : ranges.split(",")) {
            String[] ab = t.trim().split("-");
            if (ab.length != 2) throw new IllegalArgumentException("Invalid recess format: " + t);
            int a = Integer.parseInt(ab[0].trim());
            int b = Integer.parseInt(ab[1].trim());
            res.add(new int[]{a, b});
        }
        return res;
    }

    private Map<String, Map<String, SubjectPlan>> parsePlanCsv(String csv) {
        Map<String, Map<String, SubjectPlan>> out = new HashMap<>();
        if (csv == null || csv.trim().isEmpty()) return out;

        String[] lines = csv.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] cols = line.split(",");
            if (cols.length >= 6) {
                String division = cols[0].trim();
                String subject = cols[1].trim();
                int lec = Integer.parseInt(cols[2].trim());
                int lab = Integer.parseInt(cols[3].trim());
                String lecFac = cols[4].trim();
                String labFac = cols[5].trim();

                SubjectPlan sp = getOrCreate(out, division, subject);
                sp.subject = subject;
                sp.lecturesPerWeek = Math.max(0, lec);
                sp.labsPerWeek = Math.max(0, lab);
                sp.lectureFaculty = lecFac.isEmpty() ? ("Faculty-" + subject) : lecFac;
                sp.labFaculty = labFac.isEmpty() ? sp.lectureFaculty : labFac;
            }
        }
        return out;
    }

    private Map<String, Map<String, SubjectPlan>> parsePlanJson(String json) {
        Map<String, Map<String, SubjectPlan>> out = new HashMap<>();
        try {
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
            if (json.isEmpty()) return out;

            String[] objs = json.split("\\}\\s*,\\s*\\{");
            for (String raw : objs) {
                String obj = raw;
                if (!obj.startsWith("{")) obj = "{" + obj;
                if (!obj.endsWith("}")) obj = obj + "}";
                String division = extractJsonString(obj, "division");
                String subject = extractJsonString(obj, "subject");
                int lectures = parseIntSafe(extractJsonString(obj, "lectures"), 0);
                int labs = parseIntSafe(extractJsonString(obj, "labs"), 0);
                String lecFac = extractJsonString(obj, "lecFac");
                String labFac = extractJsonString(obj, "labFac");

                SubjectPlan sp = getOrCreate(out, division, subject);
                sp.subject = subject;
                sp.lecturesPerWeek = lectures;
                sp.labsPerWeek = labs;
                sp.lectureFaculty = lecFac.isEmpty() ? ("Faculty-" + subject) : lecFac;
                sp.labFaculty = labFac.isEmpty() ? sp.lectureFaculty : labFac;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Failed to parse planData JSON: " + e.getMessage());
        }
        return out;
    }

    private String extractJsonString(String obj, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"");
        java.util.regex.Matcher m = p.matcher(obj);
        return m.find() ? m.group(1) : "";
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private SubjectPlan getOrCreate(
            Map<String, Map<String, SubjectPlan>> map,
            String division,
            String subject
    ) {
        Map<String, SubjectPlan> bySubject =
                map.computeIfAbsent(division, k -> new HashMap<>());
        return bySubject.computeIfAbsent(subject, k -> new SubjectPlan());
    }

    private Map<String, String> buildDivisionTablesHtml(
            List<String> days,
            int numSlots,
            Map<String, Map<String, Map<Integer, Lecture>>> timetable
    ) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String division : timetable.keySet()) {
            StringBuilder sb = new StringBuilder();

            sb.append("<table data-division=\"").append(escapeHtml(division)).append("\">");

            // header
            sb.append("<thead><tr>");
            sb.append("<th style=\"width:70px\">Slot</th>");
            for (String day : days) {
                sb.append("<th>").append(escapeHtml(day)).append("</th>");
            }
            sb.append("</tr></thead>");

            // body
            sb.append("<tbody>");
            for (int slot = 1; slot <= numSlots; slot++) {
                sb.append("<tr>");
                sb.append("<td>").append("S").append(slot).append("</td>");

                for (String day : days) {
                    Map<Integer, Lecture> dayMap =
                            timetable.getOrDefault(division, Collections.emptyMap())
                                    .getOrDefault(day, Collections.emptyMap());
                    Lecture lec = dayMap.get(slot);
                    String kind = "FREE";
                    if (lec != null && lec.getSessionType() != null) {
                        kind = lec.getSessionType().toUpperCase(Locale.ROOT);
                    }

                    String cls;
                    switch (kind) {
                        case "RECESS": cls = "recess"; break;
                        case "LAB": cls = "lab"; break;
                        case "LECTURE": cls = "lec"; break;
                        default: cls = "free";
                    }

                    sb.append("<td class=\"").append(cls).append("\">")
                            .append("<div class=\"cell\">");

                    if ("RECESS".equals(kind)) {
                        sb.append("<span class=\"pill recess\">RECESS</span>");
                    } else if ("LAB".equals(kind)) {
                        sb.append("<span class=\"pill lab\">LAB</span>");
                    } else if ("LECTURE".equals(kind)) {
                        sb.append("<span class=\"pill lec\">LECTURE</span>");
                    } else {
                        sb.append("<span class=\"pill free\">Free</span>");
                    }

                    if (!"RECESS".equals(kind) && !"FREE".equals(kind) && lec != null) {
                        String faculty = lec.getFaculty() != null ? lec.getFaculty() : "---";
                        String subject = lec.getSubject() != null ? lec.getSubject() : "---";
                        sb.append("<span>")
                                .append(escapeHtml(faculty)).append(" (").append(escapeHtml(subject)).append(")")
                                .append("</span>");
                    }

                    sb.append("</div></td>");
                }

                sb.append("</tr>");
            }
            sb.append("</tbody></table>");

            out.put(division, sb.toString());
        }
        return out;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;");
    }
}
