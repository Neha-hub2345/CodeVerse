package com.example.timetablegenerator.service;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

import com.example.timetablegenerator.database.DBConnection;

@Service
public class TimetableService {

    // --------------------------- INNER CLASSES ---------------------------
    public static class Lecture {
        private String subject;
        private String faculty;
        private String sessionType; // "Lecture", "Lab", "RECESS"

        public Lecture(String subject, String faculty, String sessionType) {
            this.subject = subject;
            this.faculty = faculty;
            this.sessionType = sessionType;
        }

        public String getSubject() { return subject; }
        public String getFaculty() { return faculty; }
        public String getSessionType() { return sessionType; }

        @Override
        public String toString() {
            if ("RECESS".equalsIgnoreCase(sessionType)) return "[RECESS]";
            String s = (subject != null && !subject.isEmpty()) ? subject : "---";
            String f = (faculty != null && !faculty.isEmpty()) ? faculty : "---";
            String t = (sessionType != null && !sessionType.isEmpty()) ? sessionType : "Lecture";
            return f + " (" + s + ") - " + t;
        }
    }

    /** Keep SubjectPlan inside TimetableService so controller can use TimetableService.SubjectPlan */
    public static class SubjectPlan {
        public String subject;
        public int lecturesPerWeek;
        public int labsPerWeek;
        public String lectureFaculty;
        public String labFaculty;
    }

    // --------------------------- FIELDS ---------------------------
    private List<String> days = new ArrayList<>();
    private int numSlots;
    private List<String> divisions = new ArrayList<>();
    private int maxLecturesPerDay;
    private int totalLectures; // per subject per division per week (uniform mode)
    private int totalLabs;     // per lab-subject per division per week (uniform mode)
    private List<String> subjectNames = Collections.emptyList(); // merged (subjects ∪ labSubjects)
    private List<String> labSubjects = Collections.emptyList();  // exactly what user entered
    private List<int[]> recesses = new ArrayList<>();

    // plan mode
    private Map<String, Map<String, SubjectPlan>> planByDivision = new HashMap<>(); // division -> subject -> plan

    // timetable[division][day][slot]
    private final Map<String, Map<String, Map<Integer, Lecture>>> timetable = new HashMap<>();

    // prevent more than one lab block per day per division
    private final Map<String, Set<String>> divisionDayHasLab = new HashMap<>();

    // --------------------------- INPUT SETUP ---------------------------
    public void setInputs(List<String> days, int numSlots, List<String> divisions,
                          List<String> subjectNames, List<String> labSubjects,
                          List<int[]> recesses, int maxLecturesPerDay,
                          int totalLectures, int totalLabs) {

        this.days = new ArrayList<>(days);
        this.numSlots = numSlots;
        this.divisions = new ArrayList<>(divisions);

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (subjectNames != null) merged.addAll(subjectNames);
        if (labSubjects != null) merged.addAll(labSubjects);
        this.subjectNames = new ArrayList<>(merged);

        this.labSubjects = (labSubjects != null) ? labSubjects : Collections.emptyList();

        this.recesses = (recesses != null) ? recesses : new ArrayList<>();
        this.maxLecturesPerDay = maxLecturesPerDay;
        this.totalLectures = totalLectures;
        this.totalLabs = totalLabs;

        this.planByDivision = new HashMap<>();
    }

    public void setInputsWithPlan(List<String> days, int numSlots, List<String> divisions,
                                  List<int[]> recesses, int maxLecturesPerDay,
                                  Map<String, Map<String, SubjectPlan>> planByDivision) {

        this.days = new ArrayList<>(days);
        this.numSlots = numSlots;
        this.divisions = new ArrayList<>(divisions);
        this.recesses = (recesses != null) ? recesses : new ArrayList<>();
        this.maxLecturesPerDay = maxLecturesPerDay;
        this.planByDivision = (planByDivision != null) ? planByDivision : new HashMap<>();

        LinkedHashSet<String> subs = new LinkedHashSet<>();
        for (Map<String, SubjectPlan> m : this.planByDivision.values()) subs.addAll(m.keySet());
        this.subjectNames = new ArrayList<>(subs);

        this.labSubjects = Collections.emptyList();
        this.totalLectures = 0;
        this.totalLabs = 0;
    }

    // --------------------------- MAIN LOGIC ---------------------------
    public boolean generateTimetable() {
        try {
            if (numSlots <= 0 || days == null || days.isEmpty() || divisions == null || divisions.isEmpty()) {
                System.out.println("❌ GEN FAIL — Invalid inputs: days/divisions empty or numSlots<=0");
                return false;
            }

            timetable.clear();
            divisionDayHasLab.clear();
            for (String division : divisions) divisionDayHasLab.put(division, new HashSet<>());

            // Base + RECESS initialization
            for (String division : divisions) {
                Map<String, Map<Integer, Lecture>> divisionTable = new HashMap<>();
                for (String day : days) {
                    Map<Integer, Lecture> daySlots = new HashMap<>();
                    for (int[] r : recesses) {
                        int start = Math.max(1, r[0]);
                        int end = Math.min(numSlots, r[1]);
                        for (int s = start; s <= end; s++) {
                            daySlots.put(s, new Lecture(null, null, "RECESS"));
                        }
                    }
                    divisionTable.put(day, daySlots);
                }
                timetable.put(division, divisionTable);
            }

            // Track faculty load + collisions
            Set<String> facultyBusy = new HashSet<>(); // key: day#slot#faculty
            Map<String, Map<String, Integer>> facultyDayCount = new HashMap<>();

            if (!planByDivision.isEmpty()) {
                // PLAN MODE
                for (Map<String, SubjectPlan> subMap : planByDivision.values()) {
                    for (SubjectPlan sp : subMap.values()) {
                        for (String fac : facultiesOf(sp)) {
                            facultyDayCount.putIfAbsent(fac, new HashMap<>());
                            for (String day : days) facultyDayCount.get(fac).putIfAbsent(day, 0);
                        }
                    }
                }

                // Labs
                for (String division : divisions) {
                    Map<String, SubjectPlan> subs = planByDivision.getOrDefault(division, Collections.emptyMap());
                    for (SubjectPlan sp : subs.values()) {
                        int labsToPlace = Math.max(0, sp.labsPerWeek);
                        if (labsToPlace == 0) continue;

                        String faculty = (sp.labFaculty != null && !sp.labFaculty.isEmpty())
                                ? sp.labFaculty : sp.lectureFaculty;

                        while (labsToPlace > 0) {
                            boolean placed = false;
                            outer:
                            for (String day : days) {
                                if (divisionDayHasLab.get(division).contains(day)) continue;
                                if (!hasCapacity(facultyDayCount, faculty, day, 2, maxLecturesPerDay)) continue;

                                Map<Integer, Lecture> daySlots = timetable.get(division).get(day);
                                for (int slot = 1; slot <= numSlots - 1; slot++) {
                                    if (isRecess(daySlots, slot) || isRecess(daySlots, slot + 1)) continue;
                                    if (daySlots.containsKey(slot) || daySlots.containsKey(slot + 1)) continue;
                                    if (isFacultyBusy(facultyBusy, day, slot, faculty)) continue;
                                    if (isFacultyBusy(facultyBusy, day, slot + 1, faculty)) continue;

                                    daySlots.put(slot, new Lecture(sp.subject, faculty, "Lab"));
                                    daySlots.put(slot + 1, new Lecture(sp.subject, faculty, "Lab"));
                                    markBusy(facultyBusy, day, slot, faculty);
                                    markBusy(facultyBusy, day, slot + 1, faculty);
                                    incrementCount(facultyDayCount, faculty, day, 2);
                                    divisionDayHasLab.get(division).add(day);
                                    placed = true;
                                    break outer;
                                }
                            }
                            if (!placed) {
                                System.out.println("⚠️ WARN: Could not place LAB for " + sp.subject + " in " + division);
                                break;
                            }
                            labsToPlace--;
                        }
                    }
                }

                // Lectures
                for (String division : divisions) {
                    Map<String, SubjectPlan> subs = planByDivision.getOrDefault(division, Collections.emptyMap());
                    for (SubjectPlan sp : subs.values()) {
                        int lecturesToPlace = Math.max(0, sp.lecturesPerWeek);
                        String faculty = sp.lectureFaculty;

                        int dayStartIdx = 0;
                        int slotStart = 1;

                        while (lecturesToPlace > 0) {
                            boolean placed = false;

                            for (int d = 0; d < days.size() && !placed; d++) {
                                String day = days.get((dayStartIdx + d) % days.size());
                                Map<Integer, Lecture> daySlots = timetable.get(division).get(day);

                                if (!hasCapacity(facultyDayCount, faculty, day, 1, maxLecturesPerDay)) continue;

                                for (int s = slotStart; s <= numSlots; s++) {
                                    if (isRecess(daySlots, s)) continue;
                                    if (daySlots.containsKey(s)) continue;
                                    if (isFacultyBusy(facultyBusy, day, s, faculty)) continue;

                                    Lecture prev = daySlots.get(s - 1);
                                    if (prev != null && faculty.equals(prev.getFaculty()) &&
                                            !"RECESS".equalsIgnoreCase(prev.getSessionType())) {
                                        continue;
                                    }

                                    daySlots.put(s, new Lecture(sp.subject, faculty, "Lecture"));
                                    markBusy(facultyBusy, day, s, faculty);
                                    incrementCount(facultyDayCount, faculty, day, 1);

                                    placed = true;
                                    lecturesToPlace--;
                                    dayStartIdx = (dayStartIdx + 1) % days.size();
                                    slotStart = (s % numSlots) + 1;
                                    break;
                                }
                            }

                            if (!placed) {
                                System.out.println("⚠️ WARN: Could not place LECTURE for " + sp.subject + " in " + division);
                                break;
                            }
                        }
                    }
                }

                fillRemainingSlotsPlanMode(facultyDayCount, facultyBusy);

            } else {
                // UNIFORM MODE
                Map<String, String> facultyBySubject = new HashMap<>();
                for (String sub : subjectNames) facultyBySubject.put(sub, "Faculty-" + sub.trim());

                for (String fac : facultyBySubject.values()) {
                    facultyDayCount.putIfAbsent(fac, new HashMap<>());
                    for (String day : days) facultyDayCount.get(fac).putIfAbsent(day, 0);
                }

                // Labs
                for (String division : divisions) {
                    for (String subject : labSubjects) {
                        int labsToPlace = Math.max(0, totalLabs);
                        String faculty = facultyBySubject.getOrDefault(subject, "Faculty-" + subject);

                        while (labsToPlace > 0) {
                            boolean placed = false;
                            outer:
                            for (String day : days) {
                                if (divisionDayHasLab.get(division).contains(day)) continue;
                                if (!hasCapacity(facultyDayCount, faculty, day, 2, maxLecturesPerDay)) continue;

                                Map<Integer, Lecture> daySlots = timetable.get(division).get(day);
                                for (int s = 1; s <= numSlots - 1; s++) {
                                    if (isRecess(daySlots, s) || isRecess(daySlots, s + 1)) continue;
                                    if (daySlots.containsKey(s) || daySlots.containsKey(s + 1)) continue;
                                    if (isFacultyBusy(facultyBusy, day, s, faculty)) continue;
                                    if (isFacultyBusy(facultyBusy, day, s + 1, faculty)) continue;

                                    daySlots.put(s, new Lecture(subject, faculty, "Lab"));
                                    daySlots.put(s + 1, new Lecture(subject, faculty, "Lab"));
                                    markBusy(facultyBusy, day, s, faculty);
                                    markBusy(facultyBusy, day, s + 1, faculty);
                                    incrementCount(facultyDayCount, faculty, day, 2);
                                    divisionDayHasLab.get(division).add(day);

                                    placed = true;
                                    break outer;
                                }
                            }
                            if (!placed) {
                                System.out.println("⚠️ WARN: Could not place LAB for subject " + subject + " in " + division);
                                break;
                            }
                            labsToPlace--;
                        }
                    }
                }

                // Lectures
                for (String division : divisions) {
                    for (String subject : subjectNames) {
                        int lecturesToPlace = Math.max(0, totalLectures);
                        String faculty = facultyBySubject.getOrDefault(subject, "Faculty-" + subject);

                        int dayStartIdx = 0;
                        int slotStart = 1;

                        while (lecturesToPlace > 0) {
                            boolean placed = false;

                            for (int d = 0; d < days.size() && !placed; d++) {
                                String day = days.get((dayStartIdx + d) % days.size());
                                Map<Integer, Lecture> daySlots = timetable.get(division).get(day);

                                if (!hasCapacity(facultyDayCount, faculty, day, 1, maxLecturesPerDay)) continue;

                                for (int s = slotStart; s <= numSlots; s++) {
                                    if (isRecess(daySlots, s)) continue;
                                    if (daySlots.containsKey(s)) continue;
                                    if (isFacultyBusy(facultyBusy, day, s, faculty)) continue;

                                    Lecture prev = daySlots.get(s - 1);
                                    if (prev != null && faculty.equals(prev.getFaculty()) &&
                                            !"RECESS".equalsIgnoreCase(prev.getSessionType())) continue;

                                    daySlots.put(s, new Lecture(subject, faculty, "Lecture"));
                                    markBusy(facultyBusy, day, s, faculty);
                                    incrementCount(facultyDayCount, faculty, day, 1);

                                    placed = true;
                                    lecturesToPlace--;
                                    dayStartIdx = (dayStartIdx + 1) % days.size();
                                    slotStart = (s % numSlots) + 1;
                                    break;
                                }
                            }
                            if (!placed) {
                                System.out.println("⚠️ WARN: Could not place LECTURE for subject " + subject + " in " + division);
                                break;
                            }
                        }
                    }
                }

                fillRemainingSlotsUniformMode(facultyDayCount, facultyBusy);
            }

            System.out.println("✅ Timetable generated successfully with " +
                    divisions.size() + " divisions and " + days.size() + " days (" + numSlots + " slots each).");

            System.out.println("✅ GEN OK — divisions=" + divisions.size() +
                    ", days=" + days.size() + ", numSlots=" + numSlots);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --------------------------- HELPERS ---------------------------
    private void fillRemainingSlotsPlanMode(Map<String, Map<String, Integer>> facultyDayCount, Set<String> facultyBusy) {
        for (String division : divisions) {
            Map<String, Map<Integer, Lecture>> divTable = timetable.get(division);
            Map<String, SubjectPlan> subs = planByDivision.getOrDefault(division, Collections.emptyMap());
            for (String day : days) {
                Map<Integer, Lecture> daySlots = divTable.get(day);
                for (int slot = 1; slot <= numSlots; slot++) {
                    if (daySlots.containsKey(slot) || isRecess(daySlots, slot)) continue;

                    for (SubjectPlan sp : subs.values()) {
                        String faculty = sp.lectureFaculty;
                        if (!hasCapacity(facultyDayCount, faculty, day, 1, maxLecturesPerDay)) continue;
                        if (isFacultyBusy(facultyBusy, day, slot, faculty)) continue;

                        Lecture prev = daySlots.get(slot - 1);
                        if (prev != null && faculty.equals(prev.getFaculty()) &&
                                !"RECESS".equalsIgnoreCase(prev.getSessionType())) continue;

                        daySlots.put(slot, new Lecture(sp.subject, faculty, "Lecture"));
                        markBusy(facultyBusy, day, slot, faculty);
                        incrementCount(facultyDayCount, faculty, day, 1);
                        break;
                    }
                }
            }
        }
    }

    private void fillRemainingSlotsUniformMode(Map<String, Map<String, Integer>> facultyDayCount, Set<String> facultyBusy) {
        Map<String, String> facultyBySubject = new HashMap<>();
        for (String sub : subjectNames) facultyBySubject.put(sub, "Faculty-" + sub.trim());

        for (String division : divisions) {
            Map<String, Map<Integer, Lecture>> divTable = timetable.get(division);
            for (String day : days) {
                Map<Integer, Lecture> daySlots = divTable.get(day);
                for (int slot = 1; slot <= numSlots; slot++) {
                    if (daySlots.containsKey(slot) || isRecess(daySlots, slot)) continue;

                    for (String subject : subjectNames) {
                        String faculty = facultyBySubject.get(subject);
                        if (!hasCapacity(facultyDayCount, faculty, day, 1, maxLecturesPerDay)) continue;
                        if (isFacultyBusy(facultyBusy, day, slot, faculty)) continue;

                        Lecture prev = daySlots.get(slot - 1);
                        if (prev != null && faculty.equals(prev.getFaculty()) &&
                                !"RECESS".equalsIgnoreCase(prev.getSessionType())) continue;

                        daySlots.put(slot, new Lecture(subject, faculty, "Lecture"));
                        markBusy(facultyBusy, day, slot, faculty);
                        incrementCount(facultyDayCount, faculty, day, 1);
                        break;
                    }
                }
            }
        }
    }

    private List<String> facultiesOf(SubjectPlan sp) {
        Set<String> s = new LinkedHashSet<>();
        if (sp.lectureFaculty != null && !sp.lectureFaculty.isEmpty()) s.add(sp.lectureFaculty);
        if (sp.labFaculty != null && !sp.labFaculty.isEmpty()) s.add(sp.labFaculty);
        return new ArrayList<>(s);
    }

    private boolean isRecess(Map<Integer, Lecture> daySlots, int slot) {
        Lecture l = daySlots.get(slot);
        return l != null && "RECESS".equalsIgnoreCase(l.getSessionType());
    }

    private boolean isFacultyBusy(Set<String> busy, String day, int slot, String faculty) {
        return busy.contains(day + "#" + slot + "#" + faculty);
    }

    private void markBusy(Set<String> busy, String day, int slot, String faculty) {
        busy.add(day + "#" + slot + "#" + faculty);
    }

    private void incrementCount(Map<String, Map<String, Integer>> count,
                                String faculty, String day, int delta) {
        count.putIfAbsent(faculty, new HashMap<>());
        count.get(faculty).put(day, count.get(faculty).getOrDefault(day, 0) + delta);
    }

    private boolean hasCapacity(Map<String, Map<String, Integer>> facultyDayCount,
                                String faculty, String day, int needSlotsToday, int maxPerDay) {
        int used = facultyDayCount.getOrDefault(faculty, Collections.emptyMap()).getOrDefault(day, 0);
        return used + needSlotsToday <= maxPerDay;
    }

    // --------------------------- DATABASE SAVE ---------------------------
    /** Convenience: auto create run/version if caller forgets. */
    public boolean saveToDatabase() {
        UUID runId = java.util.UUID.randomUUID();
        int version = 1;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(version),0)+1 FROM allocation")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) version = rs.getInt(1);
            }
        } catch (Exception ignore) {}
        String runName = "Run " + java.time.LocalDateTime.now();
        return saveToDatabase(runId, version, runName);
    }

    /** New: writes run_meta then rows with run_id/version. */
    public boolean saveToDatabase(java.util.UUID runId, int version, String runName) {
        if (timetable == null || timetable.isEmpty()) {
            System.out.println("⚠️ No timetable data to save.");
            return false;
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1) ensure run_meta
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO run_meta(run_id, run_name) VALUES(?, ?) ON CONFLICT (run_id) DO NOTHING")) {
                ps.setObject(1, runId);
                ps.setString(2, runName != null ? runName : ("Run " + java.time.LocalDateTime.now()));
                ps.executeUpdate();
            }

            // 2) insert allocations
            String sql = "INSERT INTO allocation " +
                    "(divisionname, semesternumber, subjectname, facultyname, classname, dayname, slotno, session_type, generated_at, run_id, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (String division : timetable.keySet()) {
                    Map<String, Map<Integer, Lecture>> divTable = timetable.get(division);
                    for (String day : divTable.keySet()) {
                        Map<Integer, Lecture> daySlots = divTable.get(day);
                        for (Map.Entry<Integer, Lecture> e : daySlots.entrySet()) {
                            int slot = e.getKey();
                            Lecture lec = e.getValue();

                            pstmt.setString(1, division);
                            pstmt.setInt(2, 1); // semester (keep 1 by default or wire later)
                            pstmt.setString(3, Optional.ofNullable(lec.getSubject()).orElse("---"));
                            pstmt.setString(4, Optional.ofNullable(lec.getFaculty()).orElse("---"));
                            pstmt.setString(5, "Classroom 1"); // classroom (keep default)
                            pstmt.setString(6, day);
                            pstmt.setInt(7, slot);
                            pstmt.setString(8, lec.getSessionType());
                            pstmt.setObject(9, runId);
                            pstmt.setInt(10, version);
                            pstmt.addBatch();
                        }
                    }
                }
                pstmt.executeBatch();
            }

            conn.commit();
            System.out.println("✅ Timetable saved to PostgreSQL with run " + runId + " (v" + version + ")");
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            try { if (conn != null) conn.rollback(); } catch (Exception ignore) {}
            return false;
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); conn.close(); } catch (Exception ignore) {}
        }
    }

    public Map<String, Map<String, Map<Integer, Lecture>>> getTimetable() {
        if (timetable == null || timetable.isEmpty()) {
            System.out.println("⚠️ Timetable is empty or null at getTimetable()");
            return Collections.emptyMap();
        }
        return timetable;
    }
}
