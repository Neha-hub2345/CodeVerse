package com.example.timetablegenerator.model;

public class SubjectInfo {
    private String subject;
    private String faculty;
    private boolean hasLecture;
    private boolean hasLab;

    public SubjectInfo() {}

    public SubjectInfo(String subject, String faculty, boolean hasLecture, boolean hasLab) {
        this.subject = subject;
        this.faculty = faculty;
        this.hasLecture = hasLecture;
        this.hasLab = hasLab;
    }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }

    public boolean isHasLecture() { return hasLecture; }
    public void setHasLecture(boolean hasLecture) { this.hasLecture = hasLecture; }

    public boolean isHasLab() { return hasLab; }
    public void setHasLab(boolean hasLab) { this.hasLab = hasLab; }
}
