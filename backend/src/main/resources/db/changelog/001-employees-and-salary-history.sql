--liquibase formatted sql

--changeset salary-management:001-employees-and-salary-history
-- NOTE: primary keys must be declared exactly INTEGER for SQLite to treat them as the rowid alias
-- (auto-increment), so the entities map them with @JdbcTypeCode(INTEGER). Other 64-bit columns are BIGINT.
-- Employees: one row per person, holding the CURRENT salary.
-- Salary is stored as integer minor units (e.g. cents) in the employee's local currency.
CREATE TABLE employees (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_code TEXT    NOT NULL UNIQUE,
    first_name    TEXT    NOT NULL,
    last_name     TEXT    NOT NULL,
    email         TEXT    NOT NULL UNIQUE,
    country       TEXT    NOT NULL,            -- ISO 3166-1 alpha-2
    department    TEXT    NOT NULL,
    job_title     TEXT    NOT NULL,
    job_level     TEXT    NOT NULL,            -- e.g. L1..L7
    currency      TEXT    NOT NULL,            -- ISO 4217
    salary_minor  BIGINT  NOT NULL CHECK (salary_minor > 0),
    hire_date     DATE    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

-- Indexes for the directory filters and the grouping used by insights.
CREATE INDEX idx_employees_country    ON employees (country);
CREATE INDEX idx_employees_department ON employees (department);
CREATE INDEX idx_employees_job_title  ON employees (job_title);
CREATE INDEX idx_employees_job_level  ON employees (job_level);
CREATE INDEX idx_employees_status     ON employees (status);
CREATE INDEX idx_employees_salary     ON employees (salary_minor);
CREATE INDEX idx_employees_last_name  ON employees (last_name, first_name);

-- Append-only audit trail of salary changes (old_salary_minor is NULL for the initial salary).
CREATE TABLE salary_history (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id      BIGINT  NOT NULL REFERENCES employees (id),
    old_salary_minor BIGINT,
    new_salary_minor BIGINT  NOT NULL CHECK (new_salary_minor > 0),
    currency         TEXT    NOT NULL,
    effective_date   DATE    NOT NULL,
    reason           TEXT,
    changed_at       TIMESTAMP NOT NULL
);
CREATE INDEX idx_salary_history_employee ON salary_history (employee_id, effective_date);

--rollback DROP TABLE salary_history;
--rollback DROP TABLE employees;
