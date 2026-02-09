-- =========================================
-- Clean slate (idempotent & safe to re-run)
-- =========================================
DROP VIEW  IF EXISTS v_timetable_by_run;
DROP VIEW  IF EXISTS v_timetable_runs;
DROP VIEW  IF EXISTS v_latest_timetable;
DROP TABLE IF EXISTS allocation CASCADE;
DROP TABLE IF EXISTS class CASCADE;
DROP TABLE IF EXISTS faculty CASCADE;
DROP TABLE IF EXISTS subject CASCADE;
DROP TABLE IF EXISTS semester CASCADE;
DROP TABLE IF EXISTS division CASCADE;

-- =========================================
-- Core lookup tables
-- =========================================
CREATE TABLE division (
    divisionid   SERIAL PRIMARY KEY,
    divisionname VARCHAR(10) UNIQUE NOT NULL
);

CREATE TABLE semester (
    semesterid     SERIAL PRIMARY KEY,
    semesternumber INT NOT NULL
);

CREATE TABLE subject (
    subjectid   SERIAL PRIMARY KEY,
    subjectname VARCHAR(100) NOT NULL,
    subjectcode VARCHAR(20),
    semesterid  INT REFERENCES semester(semesterid),
    CONSTRAINT unique_subject UNIQUE (subjectname, semesterid)
);

CREATE TABLE faculty (
    facultyid   SERIAL PRIMARY KEY,
    facultyname VARCHAR(100) UNIQUE NOT NULL,
    email       VARCHAR(100),
    phone       VARCHAR(15)
);

CREATE TABLE class (
    classid   SERIAL PRIMARY KEY,
    classname VARCHAR(50) UNIQUE NOT NULL
);

-- =========================================
-- Allocation (flat writes per timetable run)
-- =========================================
CREATE TABLE allocation (
    allocationid   SERIAL PRIMARY KEY,
    divisionname   VARCHAR(50)   NOT NULL,
    semesternumber INT           NOT NULL,
    subjectname    VARCHAR(100)  NOT NULL,
    facultyname    VARCHAR(100)  NOT NULL,
    classname      VARCHAR(50)   NOT NULL,
    dayname        VARCHAR(20)   NOT NULL,
    slotno         INT           NOT NULL,
    session_type   VARCHAR(20)   NOT NULL,
    generated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Versioning fields (nullable so old inserts still work)
    run_id         UUID,
    version        INT
);

-- Optional hard rule (single booking per run)
-- ALTER TABLE allocation
--   ADD CONSTRAINT uq_alloc_run_div_day_slot UNIQUE (run_id, divisionname, dayname, slotno);

-- =========================================
-- Helpful indexes
-- =========================================
CREATE INDEX IF NOT EXISTS idx_alloc_div_day_slot   ON allocation(divisionname, dayname, slotno);
CREATE INDEX IF NOT EXISTS idx_alloc_faculty_day    ON allocation(facultyname, dayname, slotno);
CREATE INDEX IF NOT EXISTS idx_alloc_subject        ON allocation(subjectname);
CREATE INDEX IF NOT EXISTS idx_alloc_generated_at   ON allocation(generated_at);
CREATE INDEX IF NOT EXISTS idx_alloc_run_id         ON allocation(run_id);
CREATE INDEX IF NOT EXISTS idx_alloc_version        ON allocation(version);

-- =========================================
-- Convenience views (robust if run_id/version are NULL)
-- =========================================

-- Latest by timestamp (quick check)
CREATE OR REPLACE VIEW v_latest_timetable AS
SELECT
  divisionname, dayname, slotno,
  subjectname, facultyname, classname,
  session_type, semesternumber, generated_at, run_id, version
FROM allocation
WHERE generated_at = (SELECT MAX(generated_at) FROM allocation)
ORDER BY divisionname, dayname, slotno;

-- History list (keeps old code happy with 'generated_at' & 'rows')
-- Also exposes run_key, run_id, version for the new UI.
CREATE OR REPLACE VIEW v_timetable_runs AS
WITH key_rows AS (
  SELECT
    -- Stable key per run: use run_id if present else a high-precision timestamp text
    COALESCE(run_id::text, to_char(generated_at,'YYYY-MM-DD"T"HH24:MI:SS.USOF')) AS run_key,
    run_id,
    generated_at
  FROM allocation
),
agg AS (
  SELECT
    run_key,
    MIN(run_id)               AS run_id,        -- may be NULL if app didn't pass run_id
    MAX(generated_at)         AS generated_at,  -- representative timestamp for the run
    COUNT(*)                  AS rows           -- keep this name for existing Java
  FROM key_rows
  GROUP BY run_key
)
SELECT
  run_key,
  run_id,
  generated_at,
  rows,
  DENSE_RANK() OVER (ORDER BY generated_at) AS version
FROM agg
ORDER BY generated_at DESC;

-- All rows for a specific run (filter by run_key or run_id)
CREATE OR REPLACE VIEW v_timetable_by_run AS
WITH rows_with_keys AS (
  SELECT
    COALESCE(run_id::text, to_char(generated_at,'YYYY-MM-DD"T"HH24:MI:SS.USOF')) AS run_key,
    run_id, generated_at, divisionname, dayname, slotno,
    subjectname, facultyname, classname, session_type, semesternumber
  FROM allocation
),
run_versions AS (
  SELECT
    run_key,
    MAX(generated_at) AS generated_at,
    DENSE_RANK() OVER (ORDER BY MAX(generated_at)) AS version
  FROM rows_with_keys
  GROUP BY run_key
)
SELECT
  r.run_key,
  r.run_id,
  v.version,
  r.generated_at,
  r.divisionname, r.dayname, r.slotno,
  r.subjectname, r.facultyname, r.classname,
  r.session_type, r.semesternumber
FROM rows_with_keys r
JOIN run_versions v USING (run_key)
ORDER BY r.divisionname, r.dayname, r.slotno;

-- =====================================================================
-- 3. v_timetable_runs : summary view for history list
-- =====================================================================

CREATE OR REPLACE VIEW v_timetable_runs AS
SELECT
    a.run_id,
    MAX(a.version)      AS version,
    MAX(a.generated_at) AS generated_at,
    COUNT(*)            AS rows_count,
    MAX(m.run_name)     AS run_name
FROM allocation a
LEFT JOIN run_meta m ON m.run_id = a.run_id
GROUP BY a.run_id
ORDER BY generated_at DESC;


-- =========================================
-- (Optional) seed data
-- =========================================
INSERT INTO division (divisionname) VALUES ('A'), ('B'), ('C')
ON CONFLICT (divisionname) DO NOTHING;

INSERT INTO semester (semesternumber) VALUES (1),(2),(3),(4),(5),(6)
ON CONFLICT DO NOTHING;

INSERT INTO class (classname) VALUES ('Classroom 1'), ('Classroom 2')
ON CONFLICT (classname) DO NOTHING;

INSERT INTO faculty (facultyname, email) VALUES
  ('Faculty-DBMS','dbms@example.com'),
  ('Faculty-ML','ml@example.com')
ON CONFLICT (facultyname) DO NOTHING;

INSERT INTO subject (subjectname, subjectcode, semesterid)
VALUES ('DBMS','DB101',(SELECT semesterid FROM semester WHERE semesternumber=5)),
       ('ML','ML101',(SELECT semesterid FROM semester WHERE semesternumber=5))
ON CONFLICT DO NOTHING;

-- =========================================
-- Quick diagnostics
-- =========================================
-- Confirm DB/schema
SELECT current_database() AS db, current_schema() AS schema;
-- Table exists?
SELECT to_regclass('public.allocation') AS allocation_table;
-- Latest run count
SELECT COUNT(*) AS rows_latest_run
FROM allocation
WHERE generated_at = (SELECT MAX(generated_at) FROM allocation);
-- Latest snapshot
SELECT * FROM v_latest_timetable
ORDER BY divisionname, dayname, slotno
LIMIT 200;
-- History list
SELECT * FROM v_timetable_runs
ORDER BY generated_at DESC
LIMIT 20;
-- Example: inspect a specific run (pick a run_key from v_timetable_runs)
-- SELECT * FROM v_timetable_by_run WHERE run_key = '<paste run_key here>'
-- ORDER BY divisionname, dayname, slotno;
