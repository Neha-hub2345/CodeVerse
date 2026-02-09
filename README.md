# AutoChrono – Automatic Timetable Scheduler

## 📌 Project Overview
**AutoChrono (The Timetable Scheduler)** is a web-based academic scheduling system developed to automate the process of timetable generation for educational institutions. The system minimizes manual effort, reduces scheduling conflicts, and ensures efficient allocation of subjects, faculty, and time slots.

Traditional timetable preparation is time-consuming and prone to human errors such as overlapping lectures and uneven faculty workloads. AutoChrono addresses these challenges by providing a conflict-free, structured, and reliable timetable generation mechanism.

---

## 🎯 Problem Statement
In most educational institutions, timetable preparation is carried out manually, which requires significant time and coordination. This approach often results in lecture overlaps, inefficient resource utilization, and difficulties in managing changes.  

There is a need for an automated timetable scheduling system that can generate accurate and optimized timetables while satisfying institutional constraints.

---

## 🔍 Project Scope
- Designed for **small-scale educational institutions or individual departments**
- Automates timetable generation based on predefined constraints
- Ensures balanced faculty workload and conflict-free scheduling
- Simple, lightweight, and easy to operate
- Can be extended with advanced features in the future

---

## 🛠️ Technology Stack
- **Backend:** Java, Spring Boot
- **Build Tool:** Maven
- **Frontend:** Thymeleaf, Bootstrap, HTML, CSS
- **Database:** PostgreSQL
- **Database Tool:** pgAdmin
- **IDE:** Spring Tool Suite (STS)
- **Logic Implementation:** Java Collections (ArrayList, HashMap)

---

## 🧱 System Architecture
The application follows a **layered MVC architecture**:

- **Controller Layer:** Handles HTTP requests and user interactions
- **Service Layer:** Implements business logic for timetable generation, export, and history management
- **Model Layer:** Represents core entities such as subjects and allocations
- **Database Layer:** Manages persistent storage using PostgreSQL
- **View Layer:** Thymeleaf templates for user interface rendering

---

## ⚙️ Core Features
- Automatic timetable generation
- Conflict-free scheduling logic
- Faculty and subject allocation
- Timetable history tracking
- Export and download functionality
- User-friendly web interface
- Error handling with dedicated pages

---

## 🧠 Methodology Used
1. **Data Collection:** Faculty, subjects, and available time slots are collected
2. **Constraint Identification:** Rules such as avoiding lecture overlap are defined
3. **Scheduling Logic:** A constraint-based approach assigns subjects to slots
4. **Validation:** Generated timetable is checked for conflicts
5. **Output Generation:** Final timetable is displayed and stored in the database

---

## 🗄️ Database Overview
The database stores structured academic data such as:

- Faculty details
- Subject information
- Class schedules
- Timetable history

This ensures data persistence and allows easy retrieval of previous timetables.

---

## ▶️ How to Run the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/Neha-hub2345/CodeVerse.git
