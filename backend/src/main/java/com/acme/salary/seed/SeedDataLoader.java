package com.acme.salary.seed;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk-loads generated employees with batched JDBC. JPA would issue one INSERT round-trip per row
 * (IDENTITY ids disable batching), which is needlessly slow for 10,000 rows plus their history.
 */
@Component
public class SeedDataLoader {

    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SeedDataLoader(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public long employeeCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM employees", Long.class);
        return n == null ? 0 : n;
    }

    @Transactional
    public void clear() {
        jdbc.update("DELETE FROM salary_history");
        jdbc.update("DELETE FROM employees");
    }

    /** Inserts all employees and their history in one transaction. Ids are assigned explicitly (1..n). */
    @Transactional
    public void load(List<SeedEmployee> employees) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        long startId = nextId();

        List<Object[]> employeeRows = new ArrayList<>(BATCH_SIZE);
        List<Object[]> historyRows = new ArrayList<>(BATCH_SIZE * 2);
        long id = startId;
        for (SeedEmployee e : employees) {
            employeeRows.add(new Object[] {id, e.employeeCode(), e.firstName(), e.lastName(), e.email(),
                    e.country(), e.department(), e.jobTitle(), e.jobLevel(), e.currency(), e.salaryMinor(),
                    Date.valueOf(e.hireDate()), e.status().name(), now, now});
            for (SeedEmployee.SeedSalaryChange c : e.salaryChanges()) {
                historyRows.add(new Object[] {id, c.oldSalaryMinor(), c.newSalaryMinor(), e.currency(),
                        Date.valueOf(c.effectiveDate()), c.reason(), now});
            }
            id++;
            if (employeeRows.size() == BATCH_SIZE) {
                flush(employeeRows, historyRows);
            }
        }
        flush(employeeRows, historyRows);
    }

    private long nextId() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM employees", Long.class);
        return (max == null ? 0 : max) + 1;
    }

    private void flush(List<Object[]> employeeRows, List<Object[]> historyRows) {
        if (!employeeRows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO employees (id, employee_code, first_name, last_name, email, country, department,
                        job_title, job_level, currency, salary_minor, hire_date, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", employeeRows);
            employeeRows.clear();
        }
        if (!historyRows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO salary_history (employee_id, old_salary_minor, new_salary_minor, currency,
                        effective_date, reason, changed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", historyRows);
            historyRows.clear();
        }
    }
}
