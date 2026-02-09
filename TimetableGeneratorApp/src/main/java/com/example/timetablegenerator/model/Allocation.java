package com.example.timetablegenerator.model;

import jakarta.persistence.*;

@Entity
@Table(name = "allocation")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocationid")
    private int allocationid;

    @Column(name = "divisionname")
    private String divisionname;

    @Column(name = "semesternumber")
    private int semesternumber;

    @Column(name = "subjectname")
    private String subjectname;

    @Column(name = "facultyname")
    private String facultyname;

    @Column(name = "classname")
    private String classname;

    @Column(name = "dayname")
    private String dayname;

    @Column(name = "slotno")
    private int slotno;

    @Column(name = "session_type")
    private String session_type;

    public int getAllocationid() { return allocationid; }
    public void setAllocationid(int allocationid) { this.allocationid = allocationid; }

    public String getDivisionname() { return divisionname; }
    public void setDivisionname(String divisionname) { this.divisionname = divisionname; }

    public int getSemesternumber() { return semesternumber; }
    public void setSemesternumber(int semesternumber) { this.semesternumber = semesternumber; }

    public String getSubjectname() { return subjectname; }
    public void setSubjectname(String subjectname) { this.subjectname = subjectname; }

    public String getFacultyname() { return facultyname; }
    public void setFacultyname(String facultyname) { this.facultyname = facultyname; }

    public String getClassname() { return classname; }
    public void setClassname(String classname) { this.classname = classname; }

    public String getDayname() { return dayname; }
    public void setDayname(String dayname) { this.dayname = dayname; }

    public int getSlotno() { return slotno; }
    public void setSlotno(int slotno) { this.slotno = slotno; }

    public String getSession_type() { return session_type; }
    public void setSession_type(String session_type) { this.session_type = session_type; }
}
