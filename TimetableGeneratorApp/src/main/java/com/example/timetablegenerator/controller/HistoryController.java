package com.example.timetablegenerator.controller;

import com.example.timetablegenerator.service.HistoryService;
import com.example.timetablegenerator.service.HistoryService.AllocationRow;
import com.example.timetablegenerator.service.HistoryService.RunRow;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * HistoryController — shows runs list and renders a saved run using
 * the same timetable UI as the "generate" flow.
 */
@Controller
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public String listRuns(Model model) {
        List<RunRow> runs = historyService.listRuns();
        model.addAttribute("runs", runs);
        return "history";
    }

    /**
     * View a single saved run, but render it using the same timetable page.
     * This rebuilds the nested map (division -> day -> slot -> AllocationRow)
     * and pre-renders the HTML tables per-division exactly like generation flow.
     */
    @GetMapping("/history/view")
    public String viewRun(@RequestParam("runId") UUID runId, Model model) {
        // fetch rows saved for this run
        List<AllocationRow> rows = historyService.getRun(runId);

        // basic meta: try to find matching RunRow from listRuns() (cheap for small counts)
        RunRow meta = historyService.listRuns().stream()
                .filter(r -> runId.equals(r.getRunId()))
                .findFirst()
                .orElse(null);

        Integer version = (meta != null) ? meta.getVersion() : null;
        Date generatedAt = (meta != null) ? meta.getGeneratedAt() : null;
        String runName = (meta != null) ? meta.getRunName() : null;

        // if no rows found, show user-friendly message
        if (rows == null || rows.isEmpty()) {
            model.addAttribute("error", "No saved timetable rows found for run " + runId);
            return "history";
        }

        // Reconstruct nested structure: division -> day -> (slot -> AllocationRow)
        LinkedHashMap<String, LinkedHashMap<String, TreeMap<Integer, AllocationRow>>> table = new LinkedHashMap<>();
        // preserve day-order encountered
        LinkedHashSet<String> daysEncountered = new LinkedHashSet<>();
        LinkedHashSet<String> divisionsEncountered = new LinkedHashSet<>();
        int maxSlot = 0;

        // rows may already be ordered; but iterate and build structure
        for (AllocationRow ar : rows) {
            String division = ar.divisionname == null ? "Default" : ar.divisionname;
            String day = ar.dayname == null ? "Day" : ar.dayname;
            divisionsEncountered.add(division);
            daysEncountered.add(day);
            maxSlot = Math.max(maxSlot, ar.slotno);

            LinkedHashMap<String, TreeMap<Integer, AllocationRow>> byDay =
                    table.computeIfAbsent(division, d -> new LinkedHashMap<>());
            TreeMap<Integer, AllocationRow> slots =
                    byDay.computeIfAbsent(day, dd -> new TreeMap<>());
            slots.put(ar.slotno, ar);
        }

        List<String> days = new ArrayList<>(daysEncountered);
        if (days.isEmpty()) {
            // fallback default
            days = Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri");
        }
        List<String> divisions = new ArrayList<>(divisionsEncountered);
        if (divisions.isEmpty()) {
            divisions = new ArrayList<>(table.keySet());
        }
        int numSlots = Math.max(1, maxSlot);

        // Build pre-rendered HTML tables per-division (same layout used by TimetableController)
        Map<String, String> htmlTables = buildDivisionTablesHtml(days, numSlots, table);

        // Populate model attributes expected by timetable.html
        model.addAttribute("htmlTables", htmlTables);
        model.addAttribute("days", days);
        model.addAttribute("numSlots", numSlots);
        model.addAttribute("divisions", divisions);

        model.addAttribute("version", version);
        model.addAttribute("runId", runId.toString());
        model.addAttribute("runName", runName);
        model.addAttribute("generatedAt", generatedAt);
        model.addAttribute("dbStatus", "Loaded from history");

        // reuse the same Thymeleaf view used for new timetables
        return "timetable";
    }

    @PostMapping("/history/delete")
    public String deleteRun(@RequestParam("runId") UUID runId) {
        historyService.deleteRun(runId);
        return "redirect:/history";
    }

    @PostMapping("/history/rename")
    public String renameRun(@RequestParam("runId") UUID runId,
                            @RequestParam("runName") String runName) {
        historyService.renameRun(runId, runName);
        return "redirect:/history";
    }

    // -------------------------
    // Helper: builds division HTML tables (same structure as TimetableController)
    // -------------------------
    private Map<String, String> buildDivisionTablesHtml(
            List<String> days,
            int numSlots,
            Map<String, LinkedHashMap<String, TreeMap<Integer, AllocationRow>>> timetable
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
                    TreeMap<Integer, AllocationRow> dayMap =
                            timetable.getOrDefault(division, new LinkedHashMap<>())
                                     .getOrDefault(day, new TreeMap<>());
                    AllocationRow ar = dayMap.get(slot);

                    String kind = "FREE";
                    if (ar != null && ar.session_type != null) {
                        kind = ar.session_type.toUpperCase(Locale.ROOT);
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

                    if (!"RECESS".equals(kind) && !"FREE".equals(kind) && ar != null) {
                        String faculty = ar.facultyname != null ? ar.facultyname : "---";
                        String subject = ar.subjectname != null ? ar.subjectname : "---";
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
